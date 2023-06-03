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

    def 'should validate empty' () {
        expect:
        new ContainerConfig().empty()
        new ContainerConfig([], null, null, null, null).empty()
        new ContainerConfig(null, [], null, null, null).empty()
        new ContainerConfig(null, null, [], null, null).empty()
        new ContainerConfig(null, null, null, '', null).empty()
        new ContainerConfig(null, null, null, null, []).empty()
        and:
        !new ContainerConfig(['x'], null, null, null, null).empty()
        !new ContainerConfig(null, ['x'], null, null, null).empty()
        !new ContainerConfig(null, null, ['x'], null, null).empty()
        !new ContainerConfig(null, null, null, 'x', null).empty()
        !new ContainerConfig(null, null, null, null, [new ContainerLayer()]).empty()
    }

    def 'should validate groovy truth' () {
        expect:
        !new ContainerConfig()
        and:
        !new ContainerConfig([], null, null, null, null)
        !new ContainerConfig(null, [], null, null, null)
        !new ContainerConfig(null, null, [], null, null)
        !new ContainerConfig(null, null, null, '', null)
        !new ContainerConfig(null, null, null, null, [])
        and:
        new ContainerConfig(['x'], null, null, null, null)
        new ContainerConfig(null, ['x'], null, null, null)
        new ContainerConfig(null, null, ['x'], null, null)
        new ContainerConfig(null, null, null, 'x', null)
        new ContainerConfig(null, null, null, null, [new ContainerLayer()])
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

    def 'should find fusion version' () {
        given:
        def l1 = new ContainerLayer( 'https://fusionfs.seqera.io/releases/v2.1.3-amd64.json' )
        def config = new ContainerConfig(['/entry/point.sh'], ['/my/cmd'], ['FOO=1'], '/work/dir', [l1])

        expect:
        config.fusionVersion() == new FusionVersion('2.1.3', 'amd64')
        and:
        new ContainerConfig().fusionVersion() == null
    }
}
