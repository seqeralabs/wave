package io.seqera.wave.api

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerConfigTest extends Specification {

    def 'should check equals and hashcode' () {
        given:
        def l1 = new ContainerLayer( 'http://foo.com', 'sha256:12345', 100, 'sha256:67890' )
        def l2 = new ContainerLayer( 'http://foo.com', 'sha256:12345', 100, 'sha256:67890' )
        def l3 = new ContainerLayer( 'http://bar.com', 'sha256:88788', 100, 'sha256:67890' )

        and:
        def c1 = new ContainerConfig(['/entry/point.sh'], ['/my/cmd'], ['FOO=1'], '/work/dir', [l1])
        def c2 = new ContainerConfig(['/entry/point.sh'], ['/my/cmd'], ['FOO=1'], '/work/dir', [l2])
        def c3 = new ContainerConfig(['/entry/xyz.sh'], ['/your/cmd'], ['BAR=2'], '/work/dir', [l3])

        expect:
        c1 == c2
        c1 != c3
        
        and:
        c1.hashCode() == c2.hashCode()
        c1.hashCode() != c3.hashCode()
    }

    def 'should copy objects' () {
        given:
        def l1 = new ContainerLayer( 'http://foo.com', 'sha256:12345', 100, 'sha256:67890' )
        def c1 = new ContainerConfig(['/entry/point.sh'], ['/my/cmd'], ['FOO=1'], '/work/dir', [l1])

        expect:
        null == ContainerConfig.copy(null)
        and:
        c1 == ContainerConfig.copy(c1)
        c1 == ContainerConfig.copy(c1,false)
        c1 == ContainerConfig.copy(c1,true)
    }

    def 'should copy objects and strip data' () {
        given:
        def l1 = new ContainerLayer( 'data:12345678890', 'sha256:12345', 100, 'sha256:67890' )
        def c1 = new ContainerConfig(['/entry/point.sh'], ['/my/cmd'], ['FOO=1'], '/work/dir', [l1])

        expect:
        c1 == ContainerConfig.copy(c1)
        c1 == ContainerConfig.copy(c1,false)
        c1 != ContainerConfig.copy(c1,true)
        and:
        ContainerConfig.copy(c1,true).layers[0].location == 'data:DATA+OMITTED'
    }
}
