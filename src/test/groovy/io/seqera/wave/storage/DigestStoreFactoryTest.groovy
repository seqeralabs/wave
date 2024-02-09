package io.seqera.wave.storage

import spock.lang.Specification

import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.util.ZipUtils

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DigestStoreFactoryTest extends Specification {

    def 'should map location to a digest store' () {
        when:
        def l1 = new ContainerLayer('http://foo.com/this/that', 'sha:1', 100, "sha:11" )
        def ret1 = DigestStoreFactory.of( l1 )
        then:
        ret1 instanceof HttpDigestStore
        ret1.location == 'http://foo.com/this/that'
        ret1.size == 100
        ret1.digest == 'sha:1'
        ret1.mediaType == 'application/vnd.docker.image.rootfs.diff.tar.gzip'


        when:
        def l2 = new ContainerLayer('docker://foo.com/this/that', 'sha:2', 200, "sha:22" )
        def r2 = DigestStoreFactory.of( l2 )
        then:
        r2 instanceof DockerDigestStore
        r2.location == 'docker://foo.com/this/that'
        r2.size == 200
        r2.digest == 'sha:2'
        r2.mediaType == 'application/vnd.docker.image.rootfs.diff.tar.gzip'


        when:
        def d3 = "data:" + 'Hello world'.bytes.encodeBase64().toString()
        def l3 = new ContainerLayer(d3, 'sha:3', 300, "sha:33" )
        def r3 = DigestStoreFactory.of( l3 )
        then:
        r3 instanceof ZippedDigestStore
        r3.bytes == 'Hello world'.bytes
        r3.size == 300
        r3.digest == 'sha:3'
        r3.mediaType == 'application/vnd.docker.image.rootfs.diff.tar.gzip'


        when:
        def d4 = "gzip:" + ZipUtils.compress('Hello world').encodeBase64().toString()
        def l4 = new ContainerLayer(d4, 'sha:4', 400, "sha:44" )
        def r4 = DigestStoreFactory.of( l4 )
        then:
        r4 instanceof ZippedDigestStore
        r4.bytes == 'Hello world'.bytes
        r4.size == 400
        r4.digest == 'sha:4'
        r4.mediaType == 'application/vnd.docker.image.rootfs.diff.tar.gzip'
    }

}
