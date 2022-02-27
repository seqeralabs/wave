package io.seqera


import io.seqera.auth.SimpleAuthProvider
import io.seqera.proxy.ProxyClient
import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ProxyClientTest extends Specification {

    def 'should call target blob' () {
        given:
        def IMAGE = 'library/hello-world'
        and:
        def proxy = new ProxyClient('registry-1.docker.io', IMAGE, new SimpleAuthProvider(
                username: Mock.DOCKER_USER,
                password: Mock.DOCKER_PAT,
                authUrl: 'auth.docker.io/token',
                service: 'registry.docker.io'))

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
        def proxy = new ProxyClient('quay.io', IMAGE, new SimpleAuthProvider(
                username: Mock.QUAY_USER,
                password: Mock.QUAY_PAT,
                authUrl: Mock.QUAY_AUTH,
                service: 'quay.io'))

        when:
        def resp1 = proxy.getString('/v2/biocontainers/fastqc/blobs/sha256:a3ed95caeb02ffe68cdd9fd84406680ae93d633cb16422d00e8a7c22955b46d4')
        and:
        then:
        resp1.statusCode() == 200
    }

}
