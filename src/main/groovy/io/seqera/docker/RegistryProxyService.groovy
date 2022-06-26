package io.seqera.docker

import java.nio.file.Path
import javax.validation.constraints.NotBlank

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.seqera.RouteHelper
import io.seqera.RouteHelper.Route
import io.seqera.auth.RegistryCredentialsProvider
import io.seqera.auth.RegistryAuthService
import io.seqera.auth.RegistryLookupService
import io.seqera.proxy.ProxyClient
import io.seqera.storage.DigestStore
import io.seqera.storage.Storage
import jakarta.inject.Inject
import jakarta.inject.Singleton
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

    @Value('${wave.arch}')
    @NotBlank
    private String arch

    @Value('${wave.layerPath:`pack/layers/layer.json`}')
    @NotBlank
    private String layerPath

    @Inject
    private RegistryLookupService registryLookup

    @Inject
    private RegistryCredentialsProvider credentialsProvider

    @Inject
    private RegistryAuthService loginService

    private ContainerScanner scanner(ProxyClient proxyClient) {
        return new ContainerScanner()
                .withArch(arch)
                .withLayerConfig(Path.of(layerPath))
                .withStorage(storage)
                .withClient(proxyClient)
    }

    private ProxyClient client(Route route) {
        final registry = registryLookup.lookup(route.registry)
        if( !registry )
            throw new IllegalArgumentException("Unable to resolve target registry for name: '$route.registry'")
        final creds = credentialsProvider.getCredentials(route.registry)
        new ProxyClient()
                .withImage(route.image)
                .withRegistry(registry)
                .withCredentials(creds)
                .withLoginService(loginService)
    }


    DigestStore handleManifest(RouteHelper.Route route, Map<String,List<String>> headers){
        ProxyClient proxyClient = client(route)

        final digest = scanner(proxyClient).resolve(route.image, route.reference, headers)
        if( digest == null )
            throw new IllegalStateException("Missing digest for request: $route")

        final req = "/v2/$route.image/manifests/$digest"
        final entry = storage.getManifest(req).orElseThrow( ()->
                new IllegalStateException("Missing cached entry for request: $req"))
        return entry
    }

    DelegateResponse handleRequest(RouteHelper.Route route, Map<String,List<String>> headers){
        ProxyClient proxyClient = client(route)
        final resp = proxyClient.getStream(route.path, headers)

        new DelegateResponse(
                statusCode: resp.statusCode(),
                headers: resp.headers().map(),
                body: resp.body()
        )
    }

    static class DelegateResponse {
        int statusCode
        Map<String,List<String>> headers
        InputStream body
    }

}
