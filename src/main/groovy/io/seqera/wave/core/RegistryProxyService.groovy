/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
import groovy.util.logging.Slf4j
import io.micronaut.cache.annotation.Cacheable
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.micronaut.core.io.buffer.ByteBuffer
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.exceptions.HttpException
import io.micronaut.reactor.http.client.ReactorStreamingHttpClient
import io.micronaut.retry.annotation.Retryable
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
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.Storage
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Flux
import static io.seqera.wave.WaveDefault.HTTP_REDIRECT_CODES
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

    @Value('${wave.httpclient.streamThreshold:50000}')
    int streamThreshold

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
        final req = route.request
        final result = !req || !req.userId
                ? credentialsProvider.getDefaultCredentials(route)
                : credentialsProvider.getUserCredentials(route, req.userId, req.workspaceId, req.towerToken, req.towerEndpoint)
        log.debug "Credentials for route path=${route.targetContainer}; user=${req?.userId}; wsp=${req?.workspaceId} => ${result}"
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
            persistenceService.updateContainerRequest(route.token, digest)
        } catch (Throwable t) {
            log.error("Unable store container request for token: $route.token", t)
        }
    }

    DelegateResponse handleRequest(RoutePath route, Map<String,List<String>> headers){
        ProxyClient proxyClient = client(route)
        final resp1 = proxyClient.head(route.path, headers)
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
            // close the response to prevent leaks
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
        // when it's a large blob return and empty body response
        if( route.isBlob() && len > streamThreshold ) {
            return new DelegateResponse(
                    statusCode: resp1.statusCode(),
                    headers: resp1.headers().map() )
        }
        // otherwise read it
        else {
            final resp2 = proxyClient.getBytes(route.path, headers)
            return new DelegateResponse(
                    statusCode: resp2.statusCode(),
                    headers: resp2.headers().map(),
                    body: resp2.body() )
        }
    }

    boolean isManifestPresent(String image){
        try {
            return isManifestPresent0(image)
        }
        catch(Exception e) {
            log.warn "Unable to check status for container image '$image' -- cause: ${e.message}"
            return false
        }
    }

    @Cacheable('cache-1min')
    @Retryable(includes=[IOException, HttpException])
    protected boolean isManifestPresent0(String image) {
        final coords = ContainerCoordinates.parse(image)
        final route = RoutePath.v2manifestPath(coords)
        final proxyClient = client(route)
        final resp = proxyClient.head(route.path, WaveDefault.ACCEPT_HEADERS)
        return resp.statusCode() == 200
    }

    static class DelegateResponse {
        int statusCode
        Map<String,List<String>> headers
        byte[] body
        String location
        boolean isRedirect() { location }
    }

    Flux<ByteBuffer<?>> streamBlob(RoutePath route, Map<String,List<String>> headers) {
        ProxyClient proxyClient = client(route)
        return proxyClient.stream(streamClient, route.path, headers)
    }
}
