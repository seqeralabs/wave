package io.seqera.wave

import java.nio.file.Paths

import groovy.json.JsonSlurper
import io.seqera.wave.model.LayerConfig
import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LayerConfigTest extends Specification {

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
        def config = new JsonSlurper().parseText(CONFIG) as LayerConfig

        then:
        config.workingDir == "/some/path"
        config.entrypoint == ["foo", "bar"]
        and:
        config.append.locationPath == Paths.get("/some/path/layer.tag.gzip")
        config.append.gzipDigest ==  "sha256:xxx"
        config.append.tarDigest ==  "sha256:zzz"
        config.append.gzipSize == 10167366

        when:
        config.append.withBase(Paths.get('/root'))
        then:
        config.append.getLocationPath() == Paths.get("/some/path/layer.tag.gzip")
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
        def config = new JsonSlurper().parseText(CONFIG) as LayerConfig

        then:
        config.workingDir == "/some/path"
        config.entrypoint == ["foo", "bar"]
        and:
        config.append.locationPath == Paths.get("layer.tag.gzip")

        when:
        config.append.withBase(Paths.get('/root/dir'))
        then:
        config.append.getLocationPath() == Paths.get("/root/dir/layer.tag.gzip")
    }
}
