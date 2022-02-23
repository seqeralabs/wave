package io.seqera

import groovy.json.JsonSlurper
import io.seqera.model.LayerConfig
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
        config.append.location == "/some/path/layer.tag.gzip"
        config.append.gzipDigest ==  "sha256:xxx"
        config.append.tarDigest ==  "sha256:zzz"

    }

}
