package io.seqera.wave.service.blob.impl

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
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
import io.seqera.wave.service.blob.BlobStore
import io.seqera.wave.service.blob.TransferStrategy
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
    private HttpClientConfig httpConfig

    private HttpClient httpClient

    @PostConstruct
    private void init() {
        httpClient = HttpClientFactory.followRedirectsHttpClient()
        log.info "Creating Blob cache service - $blobConfig"
    }

    @Override
    BlobCacheInfo retrieveBlobCache(RoutePath route, Map<String,List<String>> headers) {
        final uri = blobDownloadUri(route)
        final info = BlobCacheInfo.create(uri)
        final target = route.targetPath
        if( blobStore.storeIfAbsent(target, info) ) {
            // start download and caching job
            return storeIfAbsent(route,headers,info)
        }
        else {
            return blobStore.awaitCacheStore(target)
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
    protected List<String> s5cmd(RoutePath route, Map<String,List<String>> headers) {
        final String cacheControl = headers.find(it-> it.key.toLowerCase()=='cache-control')?.value?.first()
        final String contentType = headers.find(it-> it.key.toLowerCase()=='content-type')?.value?.first()

        final result = new ArrayList<String>(20)
        result << 's5cmd'

        if( blobConfig.storageEndpoint ) {
            result << '--endpoint-url'
            result << blobConfig.storageEndpoint
        }

        result << '--json'
        result << 'pipe'
        result << '--acl' << 'public-read'

        if( contentType ) {
            result << '--content-type'
            result << contentType
        }

        if( cacheControl ) {
            result << '--cache-control'
            result << cacheControl
        }

        // the target object storage path where the blob is going to be uploaded
        result.add( blobStorePath(route) )

        return result
    }

    protected List<String> transferCommand(RoutePath route, Map<String,List<String>> headers) {
        final curl = proxyService.curl(route, headers)
        final s5cmd = s5cmd(route, headers)

        final command = List.of(
                'sh',
                '-c',
                Escape.cli(curl) + ' | ' + Escape.cli(s5cmd) )

        log.trace "== Blob cache transfer command: ${command.join(' ')}"
        return command
    }

    protected BlobCacheInfo storeIfAbsent(RoutePath route, Map<String,List<String>> headers, BlobCacheInfo info) {
        BlobCacheInfo result
        try {
            if( blobExists(info.locationUri) && !debug ) {
                log.debug "== Blob cache exists for object '${info.locationUri}'"
                result = info.cached()
            }
            else {
                log.debug "== Blob cache begin for object '${info.locationUri}'"
                result = store(route, headers, info)
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

    protected BlobCacheInfo store(RoutePath route, Map<String,List<String>> headers, BlobCacheInfo info) {
        final target = route.targetPath
        try {
            // the transfer command to be executed
            final cli = transferCommand(route, headers)
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
        StringUtils.pathConcat(blobConfig.baseUrl, route.targetPath)
    }
}
