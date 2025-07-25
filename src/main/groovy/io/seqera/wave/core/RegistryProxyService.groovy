/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

package io.seqera.wave.core

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import com.google.common.hash.Hashing
import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Context
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.client.annotation.Client
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient
import io.micronaut.scheduling.TaskExecutors
import io.seqera.util.trace.TraceElapsedTime
import io.seqera.wave.WaveDefault
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentials
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.proxy.DelegateResponse
import io.seqera.wave.proxy.ProxyCache
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.service.CredentialsService
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.Storage
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.RegHelper
import io.seqera.util.retry.Retryable
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import static io.seqera.wave.WaveDefault.HTTP_REDIRECT_CODES
import static io.seqera.wave.WaveDefault.HTTP_RETRYABLE_ERRORS
/**
 * Proxy service that forwards incoming container request
 * to the target repository, resolving credentials and augmentation
 * dependencies
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
@Singleton
@Context
@CompileStatic
class RegistryProxyService {

    @Inject
    private Storage storage

    @Inject
    private RegistryLookupService registryLookup

    @Inject
    private RegistryCredentialsProvider credentialsProvider

    /**
     * Service to query credentials stored into tower
     */
    @Inject
    private CredentialsService credentialsService

    @Inject
    private RegistryAuthService loginService

    @Inject
    private PersistenceService persistenceService

    @Inject
    private HttpClientConfig httpConfig

    @Inject
    @Client("stream-client")
    private ReactorStreamingHttpClient streamClient

    @Inject
    private ProxyCache cache

    // note this use explicitly executors (instead of blocking) because it must be
    // platform thread executor pool (not a virtual threads pool).
    // See the details in comment where the executor is used below
    @Inject
    @Named(TaskExecutors.IO)
    private ExecutorService httpClientExecutor

    private ContainerAugmenter scanner(ProxyClient proxyClient) {
        return new ContainerAugmenter()
                .withStorage(storage)
                .withClient(proxyClient)
    }

    private ProxyClient client(RoutePath route) {
        final httpClient = HttpClientFactory.neverRedirectsHttpClient()
        final registry = registryLookup.lookup(route.registry)
        final creds = getCredentials(route)
        new ProxyClient(httpClient, httpConfig)
                .withRoute(route)
                .withImage(route.image)
                .withRegistry(registry)
                .withCredentials(creds)
                .withLoginService(loginService)
    }

    protected RegistryCredentials getCredentials(RoutePath route) {
        final result = credentialsProvider.getCredentials(route, route.identity)
        log.debug "Credentials for route path=${route.targetContainer}; identity=${route.identity} => ${result}"
        return result
    }

    DigestStore handleManifest(RoutePath route, Map<String,List<String>> headers){
        ProxyClient proxyClient = client(route)

        final digest = scanner(proxyClient).resolve(route, headers)
        if( digest == null )
            throw new IllegalStateException("Missing digest for request: $route")

        // update the container metadata for this request
        updateContainerRequest(route, digest)

        // find out the target digest for the request
        final target = "$route.registry/v2/${route.image}/manifests/${digest.target}"
        return storage.getManifest(target).orElse(null)
    }

    protected void updateContainerRequest(RoutePath route, ContainerDigestPair digest) {
        if( !route.token )
            return

        try {
            persistenceService.updateContainerRequestAsync(route.token, digest)
        } catch (Throwable t) {
            log.error("Unable store container request for token: $route.token", t)
        }
    }

    static private final List<String> CACHEABLE_HEADERS = [
            'Accept',
            'Accept-Encoding',
            'Authorization',
            'Cache-Control',
            'Connection',
            'Content-Type',
            'Content-Length',
            'Content-Range',
            'Docker-Distribution-API',
            'If-Modified-Since',
            'If-None-Match',
            'If-None-Match',
            'Etag',
            'Host',
            'Location',
            'Last-Modified',
            'User-Agent',
            'Range',
            'X-Registry-Auth'
    ]

    static protected boolean isCacheableHeader(String key) {
        if( !key )
            return false
        for(int i=0; i<CACHEABLE_HEADERS.size(); i++ ) {
          if( key.equalsIgnoreCase(CACHEABLE_HEADERS.get(i)))
              return true
        }
        return false
    }

    @Deprecated
    static protected String requestKey(RoutePath route, Map<String,List<String>> headers) {
        assert route!=null, "Argument route cannot be null"
        final hasher = Hashing.sipHash24().newHasher()
        hasher.putUnencodedChars(route.stableHash())
        hasher.putUnencodedChars('/')
        if( !headers )
            headers = Map.of()
        for( Map.Entry<String,List<String>> entry : headers ) {
            if( "Cache-Control".equalsIgnoreCase(entry.key) ) {
                // ignore caching when cache-control header is provided
                return null
            }
            if( !isCacheableHeader(entry.key) ) {
                continue
            }
            hasher.putUnencodedChars(entry.key)
            for( String it : entry.value ) {
                if( it )
                    hasher.putUnencodedChars(it)
                hasher.putUnencodedChars('/')
            }
            hasher.putUnencodedChars('/')
        }
        final result = hasher.hash().toString()
        if( log.isTraceEnabled() ) {
            final m = Map.of(
                    'route', route.getTargetPath(),
                    'identity', route.identity,
                    'headers', headers )
            log.trace "Proxy cache key=${result}; values=${JsonOutput.toJson(m)}"
        }
        return result
    }

    DelegateResponse handleRequest(RoutePath route, Map<String,List<String>> headers) {
        if( !cache.enabled || !route.isBlob() ) {
            return handleRequest0(route, headers)
        }
        final key = route.getTargetPath()
        return cache.getOrCompute(key,(String k)-> {
            final resp = handleRequest0(route, headers)
            // when the response is not cacheable, return null as TTL
            final ttl = route.isDigest() && resp.isCacheable() ? cache.duration : null
            return new Tuple2<DelegateResponse, Duration>(resp, ttl)
        })
    }

    @TraceElapsedTime(thresholdMillis = '${wave.trace.proxy-service.threshold:1000}')
    protected DelegateResponse handleRequest0(RoutePath route, Map<String,List<String>> headers) {
        log.debug "Request processing ${route}"
        ProxyClient proxyClient = client(route)
        final resp1 = proxyClient.getStream(route.path, headers, false)
        final redirect = resp1.headers().firstValue('Location').orElse(null)
        final status = resp1.statusCode()
        if( redirect && status in HTTP_REDIRECT_CODES ) {
            // the redirect location can be a relative path i.e. without hostname
            // therefore resolve it against the target registry hostname
            final target = proxyClient.registry.host.resolve(redirect).toString()
            final result = new DelegateResponse(
                    location: target,
                    statusCode: status,
                    headers:resp1.headers().map())
            // close the response because the body is not going to be used
            // this is needed to prevent leaks - see https://bugs.openjdk.org/browse/JDK-8308364
            RegHelper.closeResponse(resp1)
            return result
        }

        if( redirect ) {
            log.warn "Unexpected redirect location '${redirect}' with status code: ${status}"
        }
        else if( status>=300 && status<400 ) {
            log.warn "Unexpected redirect status code: ${status}; headers: ${RegHelper.dumpHeaders(resp1)}"
        }

        final len = resp1.headers().firstValueAsLong('Content-Length').orElse(0)
        // when it's a large blob return a null body response to signal that
        // the call needs to fetch the blob binary using the streaming client
        if( route.isBlob() && len > httpConfig.streamThreshold ) {
            final res = new DelegateResponse(
                    statusCode: resp1.statusCode(),
                    headers: resp1.headers().map() )
            // close the response because the body is not going to be used
            // this is needed to prevent leaks - see https://bugs.openjdk.org/browse/JDK-8308364
            RegHelper.closeResponse(resp1)
            return res
        }
        // otherwise read it and include the body input stream in the response
        // the caller must consume and close the body to prevent memory leaks
        else {
            // create the retry logic on error
            final retryable = Retryable
                    .<byte[]>of(httpConfig)
                    .onRetry((event) -> log.warn("Unable to read blob body - request: $route; event: $event"))
            // read the body - note this must use a separate *platform* thread to prevent a possible
            // deep deadlock caused by the HttpClient implementation. See problem #4 in post
            // https://medium.com/@phil_3582/java-virtual-threads-some-early-gotchas-to-look-out-for-f65df1bad0db#:~:text=The%20virtual%20threads%20can%20use,using%20complex%20constructs%20like%20CompletableFuture.
            final bb = retryable.applyAsync(()-> resp1.body().bytes, httpClientExecutor).get()
            // and compose the response
            return new DelegateResponse(
                    statusCode: resp1.statusCode(),
                    headers: resp1.headers().map(),
                    body: bb )
        }
    }

    String getImageDigest(BuildRequest request, boolean retryOnNotFound=false) {
        return getImageDigest(request.targetImage, request.identity, retryOnNotFound)
    }

    String getImageDigest(String containerImage, PlatformId identity, boolean retryOnNotFound=false) {
        try {
            return getImageDigest0(containerImage, identity, retryOnNotFound).get()
        }
        catch(Exception e) {
            log.warn "Unable to retrieve digest for image '${containerImage}' -- cause: ${e.message}"
            return null
        }
    }

    static private List<Integer> RETRY_ON_NOT_FOUND = HTTP_RETRYABLE_ERRORS + 404

    // note: return a CompletableFuture to force micronaut to use caffeine AsyncCache
    // that provides a workaround about the use of virtual threads with SyncCache
    // see https://github.com/ben-manes/caffeine/issues/1468#issuecomment-1906733926
    @Cacheable(value = 'cache-registry-proxy', atomic = true, parameters = ['image'])
    protected CompletableFuture<String> getImageDigest0(String image, PlatformId identity, boolean retryOnNotFound) {
        CompletableFuture.completedFuture(getImageDigest1(image, identity, retryOnNotFound))
    }

    protected String getImageDigest1(String image, PlatformId identity, boolean retryOnNotFound) {
        final coords = ContainerCoordinates.parse(image)
        final route = RoutePath.v2manifestPath(coords, identity)
        final proxyClient = client(route)
                .withRetryableHttpErrors(retryOnNotFound ? RETRY_ON_NOT_FOUND : HTTP_RETRYABLE_ERRORS)
        final resp = proxyClient.head(route.path, WaveDefault.ACCEPT_HEADERS)
        final result = resp.headers().firstValue('docker-content-digest').orElse(null)
        if( !result && (resp.statusCode()!=404 || retryOnNotFound) ) {
            log.warn "Unable to retrieve digest for image '$image' -- response status=${resp.statusCode()}; headers:\n${RegHelper.dumpHeaders(resp)}"
        }
        return result
    }

    Flux<ByteBuffer<?>> streamBlob(RoutePath route, Map<String,List<String>> headers) {
        ProxyClient proxyClient = client(route)
        return proxyClient.stream(streamClient, route.path, headers)
    }

    List<String> curl(RoutePath route, Map<String,String> headers) {
        ProxyClient proxyClient = client(route)
        return proxyClient.curl(route.path, headers)
    } 

}
