/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package io.seqera.wave.service.blob.impl

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.BlobCacheService
import io.seqera.wave.service.blob.BlobSigningService
import io.seqera.wave.service.blob.BlobStore
import io.seqera.wave.service.blob.TransferStrategy
import io.seqera.wave.service.blob.TransferTimeoutException
import io.seqera.wave.util.Escape
import io.seqera.wave.util.Retryable
import io.seqera.wave.util.StringUtils
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import static io.seqera.wave.WaveDefault.HTTP_SERVER_ERRORS
/**
 * Implements cache for container image layer blobs
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Requires(property = 'wave.blobCache.enabled', value = 'true')
@CompileStatic
class BlobCacheServiceImpl implements BlobCacheService {

    @Value('${wave.debug:false}')
    private Boolean debug

    @Inject
    private BlobCacheConfig blobConfig

    @Inject
    private BlobStore blobStore

    @Inject
    private RegistryProxyService proxyService

    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService executor

    @Inject
    private TransferStrategy transferStrategy

    @Inject
    private BlobSigningService signingService

    @Inject
    private HttpClientConfig httpConfig

    @Inject
    @Named('BlobS3Client')
    private S3Client s3Client

    private HttpClient httpClient

    @PostConstruct
    private void init() {
        httpClient = HttpClientFactory.followRedirectsHttpClient()
        log.info "Creating Blob cache service - $blobConfig"
    }

    @Override
    BlobCacheInfo retrieveBlobCache(RoutePath route, Map<String,List<String>> requestHeaders, Map<String,List<String>> responseHeaders) {
        final uri = blobDownloadUri(route)
        log.trace "Container blob download uri: $uri"

        final info = BlobCacheInfo.create(uri, requestHeaders, responseHeaders)
        final target = route.targetPath
        if( blobStore.storeIfAbsent(target, info) ) {
            // start download and caching job
            return storeIfAbsent(route, info)
        }
        else {
            final result = awaitCacheStore(target)
            // update the download signed uri
            return result?.withLocation(uri)
        }
    }

    protected boolean blobExists(String uri) {
        final request = HttpRequest
                .newBuilder(new URI(uri))
                .method("HEAD", HttpRequest.BodyPublishers.noBody())
                .build()

        // retry strategy
        final retryable = Retryable
                .<HttpResponse<String>>of(httpConfig)
                .retryIf((response) -> response.statusCode() in HTTP_SERVER_ERRORS)
                .onRetry((event) -> log.warn("Unable to connect '$uri' - event: $event"))

        // submit the request
        final resp = retryable.apply(()-> httpClient.send(request, HttpResponse.BodyHandlers.ofString()))
        return resp.statusCode() == 200
    }

    /**
     * Creates the s5cmd command to upload the target layer blob into the object storage
     *
     * @param route The layer blob HTTP request path
     * @param headers The layer blob HTTP headers
     * @return The s5cmd command to upload the blob into the object storage for caching purposes
     */
    protected List<String> s5cmd(RoutePath route, BlobCacheInfo info) {

        final result = new ArrayList<String>(20)
        result << 's5cmd'

        if( blobConfig.storageEndpoint ) {
            result << '--endpoint-url'
            result << blobConfig.storageEndpoint
        }

        result << '--json'
        result << 'pipe'

        if( info.contentType ) {
            result << '--content-type'
            result << info.contentType
        }

        if( info.cacheControl ) {
            result << '--cache-control'
            result << info.cacheControl
        }

        // the target object storage path where the blob is going to be uploaded
        result.add( blobStorePath(route) )

        return result
    }

    protected List<String> transferCommand(RoutePath route, BlobCacheInfo info) {
        final curl = proxyService.curl(route, info.headers)
        final s5cmd = s5cmd(route, info)

        final command = List.of(
                'sh',
                '-c',
                Escape.cli(curl) + ' | ' + Escape.cli(s5cmd) )

        log.trace "== Blob cache transfer command: ${command.join(' ')}"
        return command
    }

    protected BlobCacheInfo storeIfAbsent(RoutePath route, BlobCacheInfo info) {
        BlobCacheInfo result
        try {
            if( blobExists(info.locationUri) && !debug ) {
                log.debug "== Blob cache exists for object '${info.locationUri}'"
                result = info.cached()
            }
            else {
                log.debug "== Blob cache begin for object '${info.locationUri}'"
                result = store(route, info)
                //check if the cached blob size is correct
                result = checkUploadedBlobSize(result, route)
            }
        }
        finally {
            // use a short time-to-live for failed downloads
            // this is needed to allow re-try downloads failed for
            // temporary error conditions e.g. expired credentials
            final ttl = result.succeeded()
                    ? blobConfig.statusDuration
                    : blobConfig.statusDelay.multipliedBy(10)

            blobStore.storeBlob(route.targetPath, result, ttl)
            return result
        }
    }

    /**
     * Check the size of the blob stored in the cache
     *
     * @return {@link BlobCacheInfo} the blob cache info
     */
    protected BlobCacheInfo checkUploadedBlobSize(BlobCacheInfo info, RoutePath route) {
        if( !info.succeeded() )
            return info
        if( info.contentLength == getBlobSize(route.targetPath) )
            return info
        log.warn("== Blob cache mismatch size for uploaded object '${info.locationUri}'")
        CompletableFuture.supplyAsync(() -> deleteBlob(route.targetPath), executor)
        return info.failed("Blob uploaded does not match the expected size")
    }

    protected BlobCacheInfo store(RoutePath route, BlobCacheInfo info) {
        final target = route.targetPath
        try {
            // the transfer command to be executed
            final cli = transferCommand(route, info)
            final result = transferStrategy.transfer(info, cli)
            log.debug "== Blob cache completed for object '${target}'; status=$result.exitStatus"
            return result
        }
        catch (Throwable t) {
            log.warn "== Blob cache failed for object '${target}' - cause: ${t.message}", t
            final result = info.failed(t.message)
            return result
        }
    }

    /**
     * The S3 path where the layer blob is going to be cached
     * e.g. {@code s3://some-bucket/some/path}
     *
     * @param route The source HTTP request of container layer to be cached
     * @return The S3 path where the layer blob needs to be cached
     */
    protected String blobStorePath(RoutePath route) {
        StringUtils.pathConcat(blobConfig.storageBucket, route.targetPath)
    }


    /**
     * The HTTP URI from there the cached layer blob is going to be downloaded
     *
     * @param route The source HTTP request of container layer to be cached
     * @return The HTTP URI from the cached layer blob is going to be downloaded
     */
    protected String blobDownloadUri(RoutePath route) {
        final bucketPath = StringUtils.pathConcat(blobConfig.storageBucket, route.targetPath)
        final presignedUrl = signingService.createSignedUri(bucketPath)

        if( blobConfig.baseUrl ) {
            final p = presignedUrl.indexOf(route.targetPath)
            if( p==-1 )
                throw new IllegalStateException("Unable to match blob target path in the presigned url: $presignedUrl - target path: $route.targetPath")
            return StringUtils.pathConcat(blobConfig.baseUrl, presignedUrl.substring(p))
        }
        else
            return presignedUrl
    }

    /**
     * Await for the container layer blob download
     *
     * @param key
     *      The container blob unique key
     * @return
     *      the {@link java.util.concurrent.CompletableFuture} holding the {@link BlobCacheInfo} associated with
     *      specified blob key or {@code null} if no blob record is associated for the
     *      given key
     */
    BlobCacheInfo awaitCacheStore(String key) {
        final result = blobStore.getBlob(key)
        return result ? Waiter.awaitCompletion(blobStore, key, result) : null
    }

    /**
     * Implement waiter common logic
     */
    @CompileStatic
    private static class Waiter {

        static BlobCacheInfo awaitCompletion(BlobStore store, String key, BlobCacheInfo current) {
            final beg = System.currentTimeMillis()
            // add 10% delay gap to prevent race condition with timeout expiration
            final max = (store.timeout.toMillis() * 1.10) as long
            while( true ) {
                if( current==null ) {
                    return BlobCacheInfo.unknown()
                }

                // check is completed
                if( current.done() ) {
                    return current
                }
                // check if it's timed out
                final delta = System.currentTimeMillis()-beg
                if( delta > max )
                    throw new TransferTimeoutException("Blob cache transfer '$key' timed out")
                // sleep a bit
                Thread.sleep(store.delay.toMillis())
                // fetch the build status again
                current = store.getBlob(key)
            }
        }
    }

   /**
    * get the size of the blob stored in the cache
    *
    * @return {@link Long} the size of the blob stored in the cache
    */
   protected Long getBlobSize(String key) {
        try {
            final headObjectRequest =
                    HeadObjectRequest.builder()
                            .bucket(blobConfig.storageBucket.replaceFirst("s3://",""))
                            .key(key)
                            .build()
            final headObjectResponse = s3Client.headObject(headObjectRequest as HeadObjectRequest)
            final contentLength = headObjectResponse.contentLength()
            return contentLength ?: 0L
        }
        catch (Exception e){
            log.error("== Blob cache Error getting content length of object $key from bucket ${blobConfig.storageBucket}", e)
            return 0L
        }
    }

   /**
    * delete the blob stored in the cache
    *
    */
   protected void deleteBlob(String key) {
        try {
            final deleteObjectRequest =
                    DeleteObjectRequest.builder()
                            .bucket(blobConfig.storageBucket.replaceFirst("s3://",""))
                            .key(key)
                            .build()
            s3Client.deleteObject(deleteObjectRequest as DeleteObjectRequest)
            log.debug("== Blob cache Deleted object $key from bucket ${blobConfig.storageBucket}")
        }
        catch (Exception e){
            log.error("== Blob cache Error deleting object $key from bucket ${blobConfig.storageBucket}", e)
        }
   }
}
