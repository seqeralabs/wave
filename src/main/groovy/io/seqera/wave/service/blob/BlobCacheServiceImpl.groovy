package io.seqera.wave.service.blob

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BlobConfig
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.core.RoutePath
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.service.blob.transfer.TransferStrategy
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
@CompileStatic
class BlobCacheServiceImpl implements BlobCacheService {

    @Value('${wave.debug:false}')
    private Boolean debug

    @Inject
    private BlobConfig blobConfig

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
    }

    @Override
    CompletableFuture<BlobInfo> getBlobCacheURI(RoutePath route, Map<String,List<String>> headers) {
        final uri = blobDownloadUrl(route)
        final info = BlobInfo.create(uri)
        final target = route.targetPath
        if( blobStore.storeIfAbsent(target, info) ) {
            // start download and caching job
            // launch the build async
            CompletableFuture
                    .<BlobInfo>supplyAsync(() -> storeIfAbsent(route,headers,info), executor)
        }
        return blobStore.awaitDownload(target)
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

    protected List<String> s5cmd(RoutePath route, Map<String,List<String>> headers) {
        final String cacheControl = headers.find(it-> it.key.toLowerCase()=='cache-control')?.value?.first()
        final String contentType = headers.find(it-> it.key.toLowerCase()=='content-type')?.value?.first()

        final result = [
                's5cmd',
                '--json',
                'pipe',
                '--acl',
                'public-read']
        if( contentType ) {
            result.add('--content-type')
            result.add(contentType)
        }

        if( cacheControl ) {
            result.add('--cache-control')
            result.add(cacheControl)
        }

        // the target store path
        result.add( blobStorePath(route) )

        return result
    }

    protected List<String> transferCommand(RoutePath route, Map<String,List<String>> headers) {
        final curl = proxyService.curl(route, headers)
        final s5cmd = s5cmd(route, headers)

        final ret = List.of(
                'sh',
                '-c',
                Escape.cli(curl) + ' | ' + Escape.cli(s5cmd) )

        log.trace "== Transfer command: ${ret.join(' ')}"
        return ret
    }

    protected BlobInfo storeIfAbsent(RoutePath route, Map<String,List<String>> headers, BlobInfo info) {
        BlobInfo result
        try {
            if( blobExists(info.locationUrl) && !debug ) {
                log.debug "== Blob found in cache '${info.locationUrl}'"
                result = info.cached()
            }
            else {
                log.debug "== Initiating caching for blob '${info.locationUrl}'"
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

    protected BlobInfo store(RoutePath route, Map<String,List<String>> headers, BlobInfo info) {
        final target = route.targetPath
        try {
            // the transfer command to be executed
            final cli = transferCommand(route, headers)
            final result = transferStrategy.transfer(info, cli)
            log.debug "== Completed caching for blob '${target}'; status=$result.exitStatus"
            return result
        }
        catch (Throwable t) {
            log.debug "== Errored caching for blob '${target}' - cause: ${t.message}", t
            final result = info.failed(t.message)
            return result
        }
    }

    protected String blobStorePath(RoutePath route) {
        StringUtils.pathConcat(blobConfig.bucket, route.path)
    }

    protected String blobDownloadUrl(RoutePath route) {
        StringUtils.pathConcat(blobConfig.baseUrl, route.path)
    }
}
