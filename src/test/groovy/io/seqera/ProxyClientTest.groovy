package io.seqera

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.auth.ConfigurableAuthProvider
import io.seqera.auth.SimpleAuthProvider

import io.seqera.config.DefaultConfiguration
import io.seqera.proxy.ProxyClient
import jakarta.inject.Inject
import spock.lang.Shared
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ProxyClientTest extends Specification implements DockerRegistryContainer{

    @Inject
    @Shared
    DefaultConfiguration defaultConfiguration

    def setupSpec() {
        initRegistryContainer(defaultConfiguration)
    }

    def 'should call target blob' () {
        given:
        def IMAGE = 'library/hello-world'
        and:
        def proxy = new ProxyClient(registryURL, IMAGE, new SimpleAuthProvider())

        when:
        def resp1 = proxy.getString('/v2/library/hello-world/blobs/sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412')
        and:
        println resp1.body()
        then:
        resp1.statusCode() == 200
    }

    def 'should call target blob on quay' () {
        given:
        def IMAGE = 'biocontainers/fastqc'
        and:
        def authConfiguration = new DefaultConfiguration.RegistryConfiguration.AuthConfiguration("test")
        authConfiguration.username= Mock.QUAY_USER
        authConfiguration.password= Mock.QUAY_PAT
        authConfiguration.url= Mock.QUAY_AUTH
        authConfiguration.service= 'quay.io'

        def proxy = new ProxyClient('quay.io', IMAGE, new ConfigurableAuthProvider(authConfiguration))

        when:
        def resp1 = proxy.getString('/v2/biocontainers/fastqc/blobs/sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4')
        and:
        then:
        resp1.statusCode() == 200
    }

}
