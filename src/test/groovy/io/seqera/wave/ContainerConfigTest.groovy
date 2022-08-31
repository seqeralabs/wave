package io.seqera.wave

import spock.lang.Specification

import io.seqera.wave.util.ContainerConfigFactory
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerConfigTest extends Specification {

    def 'should deserialize layer config' () {
        given:
        def CONFIG='''
            {
              "entrypoint": ["foo", "bar"],
              "workingDir": "/some/path",
              "append": {
                "location": "/some/path/layer.tag.gzip",
                "gzipDigest": "sha256:xxx",
                "gzipSize": 10167366,
                "tarDigest": "sha256:zzz" 
              } 
            }
            '''
        when:
        def config = ContainerConfigFactory.instance.from(CONFIG)

        then:
        config.workingDir == "/some/path"
        config.entrypoint == ["foo", "bar"]
        and:
        config.layers.first().gzipDigest ==  "sha256:xxx"
        config.layers.first().tarDigest ==  "sha256:zzz"
        config.layers.first().gzipSize == 10167366

    }


    def 'should deserialize layer config with rel path' () {
        given:
        def CONFIG='''
            {
              "entrypoint": ["foo", "bar"],
              "workingDir": "/some/path",
              "append": {
                "location": "layer.tag.gzip",
                "gzipDigest": "sha256:xxx",
                "gzipSize": 10167366,
                "tarDigest": "sha256:zzz" 
              } 
            }
            '''
        when:
        def config = ContainerConfigFactory.instance.from(CONFIG)

        then:
        config.workingDir == "/some/path"
        config.entrypoint == ["foo", "bar"]

    }
}
