package io.seqera


import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ProxyClientTest extends Specification {

    def 'should call target blob' () {
        given:
        def username = "pditommaso"
        def IMAGE = 'library/hello-world'
        def pat = 'd213e955-3357-4612-8c48-fa5652ad968b'
        and:
        def proxy = new ProxyClient(IMAGE, username, pat)

        when:
        def resp1 = proxy.getString('/v2/library/hello-world/blobs/sha256:feb5d9fea6a5e9606aa995e879d862b825965ba48de054caab5ef356dc6b3412')
        and:
        println resp1.body()
        then:
        resp1.statusCode() == 200
    }

}
