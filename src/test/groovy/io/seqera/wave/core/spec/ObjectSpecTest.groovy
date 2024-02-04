package io.seqera.wave.core.spec

import spock.lang.Specification

import groovy.json.JsonSlurper
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ObjectSpecTest extends Specification {

    def 'should create an object from json string' () {
        given:
        def SPEC = '''
                {
                  "mediaType":"application/vnd.oci.image.config.v1+json",
                  "digest":"sha256:3f57d9401f8d42f986df300f0c69192fc41da28ccc8d797829467780db3dd741",
                  "size":581
               }
            '''

        when:
        def result = ObjectRef.of(SPEC)
        then:
        result == new ObjectRef(
                "application/vnd.oci.image.config.v1+json",
                "sha256:3f57d9401f8d42f986df300f0c69192fc41da28ccc8d797829467780db3dd741",
                581)
    }

    def 'should create an object' () {
        when:
        def result = ObjectRef.of([mediaType: 'foo', digest: 'sha256:12345', size: 100])
        then:
        result == new ObjectRef('foo', 'sha256:12345', 100)
    }

    def 'should create an object given a list of maps' () {
        given:
        def SPEC = '''
                [
                  {
                      "mediaType":"foo",
                      "digest":"sha256:12345",
                      "size":111
                  },
                  {
                      "mediaType":"bar",
                      "digest":"sha256:67890",
                      "size":222
                  }
                ]
            '''
        and:
        def list = new JsonSlurper().parseText(SPEC) as List
        when:
        def result = ObjectRef.of(list)
        then:
        result[0] == new ObjectRef('foo', 'sha256:12345', 111)
        result[1] == new ObjectRef('bar', 'sha256:67890', 222)
    }
}
