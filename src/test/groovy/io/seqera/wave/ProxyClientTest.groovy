package io.seqera.wave

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.SimpleRegistryCredentials
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.test.DockerRegistryContainer
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ProxyClientTest extends Specification implements DockerRegistryContainer{

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Shared
    @Value('${wave.registries.quay.username}')
    String quayUsername

    @Shared
    @Value('${wave.registries.quay.password}')
    String quayPassword

    @Inject RegistryLookupService lookupService
    @Inject RegistryAuthService loginService
    @Inject RegistryCredentialsProvider credentialsProvider

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    def 'should call target blob' () {
        given:
        def IMAGE = 'library/hello-world'
        and:
        def proxy = new ProxyClient()
                .withImage(IMAGE)
                .withRegistry(getLocalTestRegistryInfo())
                .withLoginService(loginService)

        when:
        def resp1 = proxy.getString('/v2/library/hello-world/manifests/latest')
        and:
        println resp1.body()
        then:
        resp1.statusCode() == 200
    }

    def 'should call target blob on quay' () {
        given:
        def IMAGE = 'biocontainers/fastqc'
        def registry = lookupService.lookup('quay.io')
        and:
        def proxy = new ProxyClient()
                .withImage(IMAGE)
                .withRegistry(registry)
                .withLoginService(loginService)
                .withCredentials(new SimpleRegistryCredentials(quayUsername, quayPassword))

        when:
        def resp1 = proxy.getString('/v2/biocontainers/fastqc/blobs/sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4')
        and:
        then:
        resp1.statusCode() == 200
    }

}
