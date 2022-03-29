package io.seqera.docker

import javax.validation.constraints.NotBlank

import groovy.util.logging.Slf4j
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.seqera.storage.Storage
import io.seqera.storage.DigestByteArray
import io.seqera.RouteHelper
import io.seqera.auth.AuthFactory
import io.seqera.auth.DockerAuthProvider
import io.seqera.config.Registry
import io.seqera.proxy.ProxyClient
import jakarta.inject.Singleton

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
@Singleton
@Context
class ContainerService {

    Storage storage
    AuthFactory authFactory
    ApplicationContext applicationContext

    ContainerService(Storage storage, AuthFactory authFactory, ApplicationContext applicationContext) {
        this.storage = storage
        this.authFactory = authFactory
        this.applicationContext = applicationContext
    }

    @Value('${towerreg.arch}')
    @NotBlank
    private String arch

    private ContainerScanner scanner(ProxyClient proxyClient) {
        return new ContainerScanner()
                .withArch(arch)
                .withStorage(storage)
                .withClient(proxyClient)
    }

    private ProxyClient client(Registry registry, String image) {
        DockerAuthProvider authProvider = authFactory.getProvider(registry)
        new ProxyClient(registry.host, image, authProvider)
    }

    DigestByteArray handleManifest(RouteHelper.Route route, Map<String,List<String>> headers){
        final Registry registry = findRegistry(route.registry)
        assert registry

        ProxyClient proxyClient = client(registry, route.image)

        final digest = scanner(proxyClient).resolve(route.image, route.reference, headers)
        if( digest == null )
            throw new IllegalStateException("Missing digest for request: $route")

        final req = "/v2/$route.image/manifests/$digest"
        final entry = storage.getManifest(req).orElseThrow( ()->
                new IllegalStateException("Missing cached entry for request: $req"))
        entry
    }

    DelegateResponse handleRequest(RouteHelper.Route route, Map<String,List<String>> headers){
        final Registry registry = findRegistry(route.registry)

        ProxyClient proxyClient = client(registry, route.image)
        final resp = proxyClient.getStream(route.path, headers)

        new DelegateResponse(
                statusCode: resp.statusCode(),
                headers: resp.headers().map(),
                body: resp.body()
        )
    }

    private Registry findRegistry(String name){
        Collection<Registry> registries = applicationContext.getBeansOfType(Registry)
        registries.find{ name && it.name == name} ?: registries.first()
    }

    static class DelegateResponse{
        int statusCode
        Map<String,List<String>> headers
        InputStream body
    }

}
