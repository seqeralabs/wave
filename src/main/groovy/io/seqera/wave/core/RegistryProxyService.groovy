package io.seqera.wave.core


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryCredentials
import io.seqera.wave.auth.RegistryCredentialsFactory
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.service.CredentialsService
import io.seqera.wave.storage.DigestStore
import io.seqera.wave.storage.Storage
import jakarta.inject.Inject
import jakarta.inject.Singleton

import static io.seqera.wave.proxy.ProxyClient.REDIRECT_CODES

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
@Singleton
@Context
@CompileStatic
class RegistryProxyService {

    @Inject Storage storage

    @Inject
    private RegistryLookupService registryLookup

    @Inject
    private RegistryCredentialsProvider credentialsProvider

    /**
     * Service to query credentials stored into tower
     */
    @Inject
    @Nullable
    private CredentialsService credentialsService

    @Inject
    private RegistryAuthService loginService

    @Inject
    private RegistryCredentialsFactory credentialsFactory

    private ContainerScanner scanner(ProxyClient proxyClient) {
        return new ContainerScanner()
                .withStorage(storage)
                .withClient(proxyClient)
    }

    private ProxyClient client(RoutePath route) {
        final registry = registryLookup.lookup(route.registry)
        if( !registry )
            throw new IllegalArgumentException("Unable to resolve target registry for name: '$route.registry'")
        final creds = getCredentials(route)
        new ProxyClient()
                .withRoute(route)
                .withImage(route.image)
                .withRegistry(registry)
                .withCredentials(creds)
                .withLoginService(loginService)
    }

    protected RegistryCredentials getCredentials(RoutePath route) {
        final req = route.request
        final result = credentialsProvider.getUserCredentials(route, req?.userId, req?.workspaceId)
        log.debug "Credentials for route path=${route.targetContainer} => ${result}"
        return result
    }

    DigestStore handleManifest(RoutePath route, Map<String,List<String>> headers){
        ProxyClient proxyClient = client(route)

        final digest = scanner(proxyClient).resolve(route, headers)
        if( digest == null )
            throw new IllegalStateException("Missing digest for request: $route")

        final req = "/v2/$route.image/manifests/$digest"
        final entry = storage.getManifest(req).orElse(null)
        return entry
    }

    DelegateResponse handleRequest(RoutePath route, Map<String,List<String>> headers){
        ProxyClient proxyClient = client(route)
        final resp1 = proxyClient.getString(route.path, headers, false)
        final redirect = resp1.headers().firstValue('Location').orElse(null)
        if( redirect && resp1.statusCode() in REDIRECT_CODES ) {
            return new DelegateResponse(
                    location: redirect,
                    statusCode: resp1.statusCode(),
                    headers:resp1.headers().map())
        }
        
        final resp2 = proxyClient.getStream(route.path, headers)
        new DelegateResponse(
                statusCode: resp2.statusCode(),
                headers: resp2.headers().map(),
                body: resp2.body() )
    }

    static class DelegateResponse {
        int statusCode
        Map<String,List<String>> headers
        InputStream body
        String location
        boolean isRedirect() { location }
    }

}
