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
import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.BlobCacheService
import io.seqera.wave.service.blob.BlobSigningService
import io.seqera.wave.service.blob.BlobStore
import io.seqera.wave.service.job.JobHandler
import io.seqera.wave.service.job.JobId
import io.seqera.wave.service.job.JobService
import io.seqera.wave.service.job.JobState
import io.seqera.wave.util.Escape
import io.seqera.wave.util.Retryable
import io.seqera.wave.util.StringUtils
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.WaveDefault.HTTP_SERVER_ERRORS
/**
 * Implements cache for container image layer blobs
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Named('Transfer')
@Singleton
@Requires(property = 'wave.blobCache.enabled', value = 'true')
@CompileStatic
class BlobCacheServiceImpl implements BlobCacheService, JobHandler {

    @Value('${wave.debug:false}')
    private Boolean debug

    @Inject
    private BlobCacheConfig blobConfig

    @Inject
    private BlobStore blobStore

    @Inject
    private RegistryProxyService proxyService

    @Inject
    private JobService jobService

    @Inject
    private BlobSigningService signingService

    @Inject
    private HttpClientConfig httpConfig

    private HttpClient httpClient

    @PostConstruct
    private void init() {
        httpClient = HttpClientFactory.followRedirectsHttpClient()
        log.info "Creating Blob cache service - $blobConfig"
    }

    @Override
    BlobCacheInfo retrieveBlobCache(RoutePath route, Map<String,List<String>> requestHeaders, Map<String,List<String>> responseHeaders) {
        final locationUri = blobDownloadUri(route)
        final objectUri = blobStorePath(route)
        log.trace "Container blob download uri: $locationUri; target object: $objectUri"

        final info = BlobCacheInfo.create(locationUri, objectUri, requestHeaders, responseHeaders)
        // both S3 and R2 are strongly consistent
        // therefore it's safe to check and return directly
        // if it exists (no risk of returning a partial upload)
        // https://developers.cloudflare.com/r2/reference/consistency/
        if( blobExists(info.locationUri) && !debug ) {
            log.debug "== Blob cache exists for object '${info.locationUri}'"
            return info.cached()
        }

        if( blobStore.storeIfAbsent(info.id(), info) ) {
            // start download and caching job
            store(route, info)
        }

        final result = awaitCacheStore(info.id())
        // update the download signed uri
        return result?.withLocation(locationUri)
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

    protected void store(RoutePath route, BlobCacheInfo blob) {
        log.debug "== Blob cache begin for object '${blob.locationUri}'"
        try {
            // the transfer command to be executed
            final cli = transferCommand(route, blob)
            jobService.launchTransfer(blob, cli)
        }
        catch (Throwable t) {
            log.warn "== Blob cache failed for object '${blob.objectUri}' - cause: ${t.message}", t
            final result = blob.failed(t.message)
            // update the blob status
            blobStore.storeBlob(blob.id(), result, blobConfig.failureDuration)
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
            final target = current?.locationUri ?: "(unknown)"
            while( true ) {
                if( current==null ) {
                    return BlobCacheInfo.unknown("Unable to cache blob $target")
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
    Duration jobMaxDuration(JobId job) {
        return blobConfig.transferTimeout
    }

    @Override
    void onJobCompletion(JobId job, JobState state) {
        final blob = blobStore.getBlob(job.id)
        if( !blob ) {
            log.error "== Blob cache entry unknown for job=$job [1]"
            return
        }
        if( blob.done() ) {
            log.warn "== Blob cache entry already marked as completed for job=$job [1] - entry=$blob; state=$state"
            return
        }
        // use a short time-to-live for failed downloads
        // this is needed to allow re-try caching of failure transfers
        final ttl = state.succeeded()
                ? blobConfig.statusDuration
                : blobConfig.failureDuration
        // update the blob status
        final result = state.succeeded()
                ? blob.completed(state.exitCode, state.stdout)
                : blob.failed(state.stdout)
        blobStore.storeBlob(blob.id(), result, ttl)
        log.debug "== Blob cache completed for object '${blob.objectUri}'; id=${blob.objectUri}; status=${result.exitStatus}; duration=${result.duration()}"
    }

    @Override
    void onJobException(JobId job, Throwable error) {
        final blob = blobStore.getBlob(job.id)
        if( !blob ) {
            log.error "== Blob cache entry unknown for job=$job [2]"
            return
        }
        if( blob.done() ) {
            log.warn "== Blob cache entry already marked as completed for job=$job [2] - entry=$blob; error=${error.message}"
            return
        }
        final result = blob.failed("Unexpected error caching blob '${blob.locationUri}' - job name '${job.schedulerId}'")
        log.error("== Blob cache exception for object '${blob.objectUri}'; job name=${job.schedulerId}; cause=${error.message}", error)
        blobStore.storeBlob(job.id, result, blobConfig.failureDuration)
    }

    @Override
    void onJobTimeout(JobId job) {
        final blob = blobStore.getBlob(job.id)
        if( !blob ) {
            log.error "== Blob cache entry unknown for job=$job [3]"
            return
        }
        if( blob.done() ) {
            log.warn "== Blob cache entry already marked as completed for job=$job [3] - entry=$blob; duration=${blob.duration()}"
            return
        }
        final result = blob.failed("Blob cache transfer timed out ${blob.objectUri}")
        log.warn "== Blob cache completed for object '${blob.objectUri}'; job name=${job.schedulerId}; duration=${result.duration()}"
        blobStore.storeBlob(blob.id(), result, blobConfig.failureDuration)
    }
}
