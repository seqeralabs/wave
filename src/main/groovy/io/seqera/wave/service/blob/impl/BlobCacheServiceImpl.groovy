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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.BlobCacheEnabled
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.service.blob.BlobCacheService
import io.seqera.wave.service.blob.BlobEntry
import io.seqera.wave.service.blob.BlobSigningService
import io.seqera.wave.service.blob.BlobStateStore
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobSpec
import io.seqera.wave.service.job.JobState
import io.seqera.wave.util.BucketTokenizer
import io.seqera.wave.util.Escape
import io.seqera.wave.util.StringUtils
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest
import software.amazon.awssdk.services.s3.model.HeadObjectRequest
import software.amazon.awssdk.services.s3.model.S3Exception
/**
 * Implements cache for container image layer blobs
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Named('Transfer')
@Singleton
@Requires(bean = BlobCacheEnabled)
@CompileStatic
class BlobCacheServiceImpl implements BlobCacheService, JobHandler<BlobEntry> {

    @Value('${wave.debug:false}')
    private Boolean debug

    @Inject
    private BlobCacheConfig blobConfig

    @Inject
    private BlobStateStore blobStore

    @Inject
    private RegistryProxyService proxyService

    @Inject
    private JobService jobService

    @Inject
    private BlobSigningService signingService

    @Inject
    private HttpClientConfig httpConfig

    @Inject
    @Named('BlobS3Client')
    private S3Client s3Client

    @PostConstruct
    private void init() {
        log.info "Creating Blob cache service - $blobConfig"
    }

    @Override
    BlobEntry retrieveBlobCache(RoutePath route, Map<String,List<String>> requestHeaders, Map<String,List<String>> responseHeaders) {
        final locationUri = blobDownloadUri(route)
        final objectUri = blobStorePath(route)
        log.trace "Container blob download uri: $locationUri; target object: $objectUri"

        final info = BlobEntry.create(locationUri, objectUri, requestHeaders, responseHeaders)
        // both S3 and R2 are strongly consistent
        // therefore it's safe to check and return directly
        // if it exists (no risk of returning a partial upload)
        // https://developers.cloudflare.com/r2/reference/consistency/
        if( blobExists(info.objectUri) && !debug ) {
            log.debug "== Blob cache exists for object '${info.locationUri}'"
            return info.cached()
        }

        if( blobStore.storeIfAbsent(info.getKey(), info) ) {
            // start download and caching job
            store(route, info)
        }

        final result = awaitCacheStore(info.getKey())
        // update the download signed uri
        return result?.withLocation(locationUri)
    }

    protected boolean blobExists(String blobLocation) {
        return blobSize(blobLocation) != null
    }

    /**
     * Retrieve the size of the object stored in the object storage cache
     *
     * @param blobLocation The object storage path e.g. {@code s3://bucket-name/some/path}
     * @return The size in bytes of the stored object or {@code null} if the object does not exist
     */
    protected Long blobSize(String blobLocation) {
        try {
            final object = BucketTokenizer.from(blobLocation)
            final request = HeadObjectRequest
                    .builder()
                    .bucket(object.bucket)
                    .key(object.key)
                    .build() as HeadObjectRequest
            // Execute the request
            return s3Client.headObject(request).contentLength()
        }
        catch (S3Exception e) {
            if (e.statusCode() != 404) {
                log.error "Unexpected response=${e.statusCode()} checking existence for object=${blobLocation} - cause: ${e.message}"
            }
            return null
        }
        catch (Exception e) {
            log.error "Unexpected error checking existence for object=${blobLocation} - cause: ${e.message}", e
            return null
        }
    }

    /**
     * Delete the object stored in the object storage cache. This is used to remove an invalid
     * object left behind by a failed transfer, so that it is not reported as a valid cache entry
     * by {@link #blobExists(java.lang.String)} on a subsequent request.
     *
     * @param blobLocation The object storage path e.g. {@code s3://bucket-name/some/path}
     */
    protected void deleteBlob(String blobLocation) {
        try {
            final object = BucketTokenizer.from(blobLocation)
            final request = DeleteObjectRequest
                    .builder()
                    .bucket(object.bucket)
                    .key(object.key)
                    .build() as DeleteObjectRequest
            s3Client.deleteObject(request)
            log.debug "Deleted invalid blob cache object=${blobLocation}"
        }
        catch (Exception e) {
            // the entry is marked as errored regardless, therefore a failure to clean up the
            // object must not propagate out of the job completion handling
            log.error "Unable to delete invalid blob cache object=${blobLocation} - cause: ${e.message}", e
        }
    }

    /**
     * Creates the s5cmd command to upload the target layer blob into the object storage
     *
     * @param route The layer blob HTTP request path
     * @param headers The layer blob HTTP headers
     * @return The s5cmd command to upload the blob into the object storage for caching purposes
     */
    protected List<String> s5cmd(RoutePath route, BlobEntry info) {

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

    protected List<String> transferCommand(RoutePath route, BlobEntry info) {
        final curl = proxyService.curl(route, info.headers)
        final s5cmd = s5cmd(route, info)

        // 'set -o pipefail' makes the pipeline fail when the curl command fails; without it
        // the exit status is the one of the last command i.e. s5cmd, which happily exits zero
        // after uploading an empty stream when curl failed to download the blob.
        // Note 'bash' is required instead of 'sh': in the s5cmd image /bin/sh is 'dash', which
        // does not implement 'pipefail' and aborts the whole script with "Illegal option -o
        // pipefail" before the pipeline is even executed
        final command = List.of(
                'bash',
                '-c',
                'set -o pipefail; ' + Escape.cli(curl) + ' | ' + Escape.cli(s5cmd) )

        log.trace "== Blob cache transfer command: ${command.join(' ')}"
        return command
    }

    protected void store(RoutePath route, BlobEntry blob) {
        log.debug "== Blob cache begin for object '${blob.locationUri}'"
        try {
            // the transfer command to be executed
            final cli = transferCommand(route, blob)
            jobService.launchTransfer(blob, cli)
        }
        catch (Throwable t) {
            log.warn "== Blob cache failed for object '${blob.objectUri}' - cause: ${t.message}", t
            final result = blob.errored(t.message)
            // update the blob status
            blobStore.storeBlob(blob.getKey(), result)
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
        final bucketPath = blobStorePath(route)
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
     *      the {@link java.util.concurrent.CompletableFuture} holding the {@link BlobEntry} associated with
     *      specified blob key or {@code null} if no blob record is associated for the
     *      given key
     */
    BlobEntry awaitCacheStore(String key) {
        final result = blobStore.getBlob(key)
        return result ? Waiter.awaitCompletion(blobStore, key, result) : null
    }

    /**
     * Implement waiter common logic
     */
    @CompileStatic
    private static class Waiter {

        static BlobEntry awaitCompletion(BlobStateStore store, String key, BlobEntry current) {
            final target = current?.locationUri ?: "(unknown)"
            while( true ) {
                if( current==null ) {
                    return BlobEntry.unknown("Unable to cache blob $target")
                }
                // check is completed
                if( current.done() ) {
                    return current
                }
                // sleep a bit
                Thread.sleep(store.delay.toMillis())
                // fetch the build status again
                current = store.getBlob(key)
            }
        }
    }


    // ============ handles transfer job events ============

    @Override
    BlobEntry getJobEntry(JobSpec job) {
        blobStore.getBlob(job.entryKey)
    }

    /**
     * Verify the object uploaded in the object storage cache matches the expected size, to prevent
     * an empty or truncated transfer from being cached and served as if it were valid. Note the
     * transfer job and the Wave proxy issue two separate requests to the upstream registry, therefore
     * a job can report success even when no byte was actually downloaded.
     *
     * @param entry The {@link BlobEntry} associated with the completed transfer
     * @return An error message describing the mismatch or {@code null} when the transfer is valid
     */
    protected String checkTransferredSize(BlobEntry entry) {
        final uploaded = blobSize(entry.objectUri)
        if( uploaded == null )
            return "Blob cache object '${entry.objectUri}' was not uploaded to the object storage".toString()
        // the upstream content length is not always provided, in that case only an
        // empty object can be detected as invalid
        final expected = entry.contentLength
        if( expected == null ) {
            return uploaded == 0
                    ? "Blob cache object '${entry.objectUri}' is empty".toString()
                    : null
        }
        if( uploaded != expected )
            return "Blob cache object '${entry.objectUri}' size does not match the expected content length - uploaded: ${uploaded}; expected: ${expected}".toString()
        return null
    }

    @Override
    void onJobCompletion(JobSpec job, BlobEntry entry, JobState state) {
        // the transfer job exit status only tells the command pipeline exited zero, it does not
        // guarantee the blob bytes made it to the object storage - validate before completing
        final error = state.succeeded()
                ? checkTransferredSize(entry)
                : state.stdout
        if( error ) {
            log.warn "== Blob cache transfer invalid for object '${entry.objectUri}'; operation=${job.operationName} - cause: ${error}"
            // remove whatever the failed transfer left behind: a failing pipeline can still have
            // uploaded an empty or truncated object, and blobExists() would then report it as a
            // valid cache entry on the next request, making the bad transfer sticky
            deleteBlob(entry.objectUri)
        }
        // update the entry status
        final result = !error
                ? entry.completed(state.exitCode, state.stdout)
                : entry.errored(error)
        blobStore.storeBlob(entry.getKey(), result)
        log.debug "== Blob cache completed for object '${entry.objectUri}'; operation=${job.operationName}; status=${result.exitStatus}; duration=${result.duration()}"
    }

    @Override
    void onJobException(JobSpec job, BlobEntry entry, Throwable error) {
        final result = entry.errored("Unexpected error caching blob '${entry.locationUri}' - operation '${job.operationName}'")
        log.error("== Blob cache exception for object '${entry.objectUri}'; operation=${job.operationName}; cause=${error.message}", error)
        blobStore.storeBlob(entry.getKey(), result)
    }

    @Override
    void onJobTimeout(JobSpec job, BlobEntry entry) {
        final result = entry.errored("Blob cache transfer timed out ${entry.objectUri}")
        log.warn "== Blob cache timed out for object '${entry.objectUri}'; operation=${job.operationName}; duration=${result.duration()}"
        blobStore.storeBlob(entry.key, result)
    }

    @Override
    JobSpec launchJob(JobSpec job, BlobEntry entry) {
        throw new UnsupportedOperationException("Operation launchJob not support by ${this.class.simpleName}")
    }
}
