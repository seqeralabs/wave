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

import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Context
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.client.annotation.Client
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient
import io.seqera.wave.WaveDefault
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentials
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.service.CredentialsService
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.Storage
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject
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

    DelegateResponse handleRequest(RoutePath route, Map<String,List<String>> headers){
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
            log.warn "Unexpected redirect status code: ${status}; headers: ${RegHelper.dumpHeaders(resp1.headers())}"
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
            return new DelegateResponse(
                    statusCode: resp1.statusCode(),
                    headers: resp1.headers().map(),
                    body: resp1.body() )
        }
    }

    String getImageDigest(BuildRequest request, boolean retryOnNotFound=false) {
        return getImageDigest(request.targetImage, request.identity, retryOnNotFound)
    }

    String getImageDigest(String containerImage, PlatformId identity, boolean retryOnNotFound=false) {
        try {
            return getImageDigest0(containerImage, identity, retryOnNotFound)
        }
        catch(Exception e) {
            log.warn "Unable to retrieve digest for image '${containerImage}' -- cause: ${e.message}"
            return null
        }
    }

    static private List<Integer> RETRY_ON_NOT_FOUND = HTTP_RETRYABLE_ERRORS + 404

    @Cacheable(value = 'cache-registry-proxy', atomic = true, parameters = ['image'])
    protected String getImageDigest0(String image, PlatformId identity, boolean retryOnNotFound) {
        final coords = ContainerCoordinates.parse(image)
        final route = RoutePath.v2manifestPath(coords, identity)
        final proxyClient = client(route)
                .withRetryableHttpErrors(retryOnNotFound ? RETRY_ON_NOT_FOUND : HTTP_RETRYABLE_ERRORS)
        final resp = proxyClient.head(route.path, WaveDefault.ACCEPT_HEADERS)
        final result = resp.headers().firstValue('docker-content-digest').orElse(null)
        if( !result && (resp.statusCode()!=404 || retryOnNotFound) ) {
            log.warn "Unable to retrieve digest for image '$image' -- response status=${resp.statusCode()}; headers:\n${RegHelper.dumpHeaders(resp.headers())}"
        }
        return result
    }

    @ToString(includeNames = true, includePackage = false)
    static class DelegateResponse {
        int statusCode
        Map<String,List<String>> headers
        InputStream body
        String location
        boolean isRedirect() { location }
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
