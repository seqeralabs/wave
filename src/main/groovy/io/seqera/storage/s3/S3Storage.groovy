package io.seqera.storage.s3

import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.http.MediaType
import io.micronaut.scheduling.annotation.Async
import io.seqera.config.S3StorageConfiguration
import io.seqera.config.StorageConfiguration
import io.seqera.storage.AbstractCacheStorage
import io.seqera.storage.DigestStore
import io.seqera.storage.DownloadFileExecutor
import io.seqera.storage.util.InputStreamDigestStore
import io.seqera.storage.util.LazyDigestStore
import io.seqera.storage.util.ZippedDigestStore
import io.seqera.util.TapInputStreamFilter
import jakarta.inject.Singleton
import reactor.core.publisher.Mono
import reactor.core.scheduler.Schedulers
import software.amazon.awssdk.core.ResponseInputStream
import software.amazon.awssdk.core.sync.RequestBody
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.GetObjectResponse
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Primary
@Requires(property = "towerreg.storage.s3.enabled", value = "true")
@Singleton
@Slf4j
class S3Storage extends AbstractCacheStorage {

    private static String TOWER_CONTENT_TYPE_KEY = 'tower_content_type_key'
    private static String TOWER_DIGEST_KEY = 'tower_digest_key'

    private boolean intermediateBlobs
    private String bucketName
    private S3Client s3Client
    private DownloadFileExecutor downloadFileExecutor

    S3Storage(StorageConfiguration storageConfiguration,
              S3StorageConfiguration s3StorageConfiguration,
              DownloadFileExecutor downloadFileExecutor,
              S3Client s3Client) {
        super(storageConfiguration)
        this.bucketName = s3StorageConfiguration.bucket
        this.intermediateBlobs = s3StorageConfiguration.isStoreRemotes()
        this.downloadFileExecutor = downloadFileExecutor
        this.s3Client = s3Client
    }

    @Override
    boolean containsBlob(String path) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucketName).key(path).build() as HeadObjectRequest)
            true
        } catch (Exception ignored) {
            false
        }
    }

    @Override
    Optional<DigestStore> getBlob(String path) {
        try {
            ResponseInputStream<GetObjectResponse> response = s3Client.getObject(
                    GetObjectRequest.builder().bucket(bucketName).key(path).build() as GetObjectRequest)
            InputStream inputStream = wrapInputStream(path, response)
            Map<String, String> metadata = response.response().metadata()
            String contentType = metadata.get(TOWER_CONTENT_TYPE_KEY)
            String digest = metadata.get(TOWER_DIGEST_KEY)
            long size = response.response().contentLength()
            final DigestStore result = new InputStreamDigestStore(inputStream, contentType, digest, size)
            Optional.of(result)
        } catch (Exception error) {
            log.error "Error downloading $path from S3", error
            Optional.empty()
        }
    }

    @Override
    DigestStore saveBlob(String path, byte[] content, String type, String digest) {
        log.debug "Save Blob [size: ${content.length}] ==> $path"
        Map<String, String> metadata = Map.of(TOWER_CONTENT_TYPE_KEY, type, TOWER_DIGEST_KEY, digest)
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(path).metadata(metadata).build() as PutObjectRequest,
                RequestBody.fromBytes(content))
        final result = new ZippedDigestStore(content, type, digest)
        result
    }

    @Override
    DigestStore saveBlob(String path, Path content, String type, String digest) {
        log.debug "Save Blob [size: ${Files.size(content)}] ==> $path"
        uploadFile(path, content, type, digest)
        final result = new LazyDigestStore(content, type, digest)
        result
    }

    @Override
    InputStream saveBlob(String path, InputStream inputStream, String type, String digest, long length) {
        // if there is another thread uploading the blob to S3 let current client download directly from remote repository
        if (isUploadingFlagPresent(path) || !createFlagForPath(path)) {
            return inputStream
        }

        // otherwise store the blob and serve it
        uploadStream(path, inputStream, type, digest, length)
        removeFlagForPath(path)

        def remoteInputStream = s3Client.getObject(
                GetObjectRequest.builder().bucket(bucketName).key(path).build() as GetObjectRequest)
        wrapInputStream(path, remoteInputStream)
    }

    private InputStream wrapInputStream(final String path, final InputStream inputStream){
        def pipedInputStream = new PipedInputStream()
        def pipedOutputStream = new PipedOutputStream()
        pipedInputStream.connect(pipedOutputStream)
        asyncTransferInputStream(path, inputStream, pipedOutputStream)
        pipedInputStream
    }

    @Async
    void asyncTransferInputStream(final String path, final InputStream inputStream, final OutputStream outputStream){
        inputStream.transferTo(outputStream)
        outputStream.flush()
        outputStream.close()
        log.info "Blob transferred => $path"
    }

    protected void uploadStream(final String path, final InputStream inputStream, final String type, final String digest, final long length) {
        final Map<String, String> metadata = Map.of(TOWER_CONTENT_TYPE_KEY, type, TOWER_DIGEST_KEY, digest)
        try {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(path)
                            .metadata(metadata)
                            .contentLength(length)
                            .build() as PutObjectRequest,
                    RequestBody.fromInputStream(inputStream, length))
            log.info "Save Blob [size: ${length}] ==> $path"
        } catch (Exception e) {
            log.error "Error uploading blob to S3 => $path", e
        }
    }

    private static final String FLAG_EXTENSION = 'flg'

    private boolean isUploadingFlagPresent(String path) {
        try {
            s3Client.headObject(
                    HeadObjectRequest.builder().bucket(bucketName)
                            .key("${path}.${FLAG_EXTENSION}")
                            .build() as HeadObjectRequest)
            true
        } catch (Exception ignored) {
            false
        }
    }

    private boolean createFlagForPath(String path) {
        try {
            log.info "Creating flag for $path"
            s3Client.putObject(
                    PutObjectRequest.builder().bucket(bucketName)
                            .key("${path}.${FLAG_EXTENSION}")
                            .contentType(MediaType.TEXT_PLAIN)
                            .build() as PutObjectRequest,
                    RequestBody.fromString(Instant.now().toString()))
            true
        } catch (Exception e) {
            log.error "Error creating flag for path $path", e
            false
        }
    }

    private void removeFlagForPath(String path) {
        try {
            log.info "Removing flag for $path"
            s3Client.deleteObject(
                    DeleteObjectRequest.builder().bucket(bucketName)
                            .key("${path}.${FLAG_EXTENSION}")
                            .build() as DeleteObjectRequest)
        } catch (Exception e) {
            log.error "Error deleting flag for path $path", e
        }
    }

    private void uploadFile(final String path, final Path content, final String type, final String digest) {
        Map<String, String> metadata = Map.of(TOWER_CONTENT_TYPE_KEY, type, TOWER_DIGEST_KEY, digest)
        s3Client.putObject(
                PutObjectRequest.builder().bucket(bucketName).key(path).metadata(metadata).build() as PutObjectRequest,
                RequestBody.fromFile(content))
    }
}
