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
import io.seqera.wave.service.blob.transfer.TransferQueue
import io.seqera.wave.service.blob.transfer.TransferStrategy
import io.seqera.wave.util.Escape
import io.seqera.wave.util.Retryable
import io.seqera.wave.util.StringUtils
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton
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
    private TransferStrategy transferStrategy

    @Inject
    private TransferQueue transferQueue

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
        log.trace "Container blob download uri: $locationUri"

        final info = BlobCacheInfo.create(locationUri, objectUri, requestHeaders, responseHeaders)
        // both S3 and R2 are strongly consistent
        // therefore it's safe to return it immediately to return directly
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

    protected void store(RoutePath route, BlobCacheInfo info) {
        log.debug "== Blob cache begin for object '${info.locationUri}'"
        try {
            // the transfer command to be executed
            final cli = transferCommand(route, info)
            transferStrategy.transfer(info, cli)
            // signal the transfer started
            transferQueue.offer(info.id())
        }
        catch (Throwable t) {
            log.warn "== Blob cache failed for object '${info.objectUri}' - cause: ${t.message}", t
            final result = info.failed(t.message)
            // update the blob status
            blobStore.storeBlob(info.id(), result, blobConfig.failureDuration)
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

}
