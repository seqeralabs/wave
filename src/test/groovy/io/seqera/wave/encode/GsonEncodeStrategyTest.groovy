package io.seqera.wave.encode

import spock.lang.Specification

import java.time.Instant

import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.storage.LazyDigestStore
import io.seqera.wave.storage.reader.DataContentReader

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class GsonEncodeStrategyTest extends Specification {

    def 'should encode and decode build result' () {
        given:
        def encoder = new GsonEncodeStrategy<BuildResult>() { } 
        and:
        def build = BuildResult.completed('1', 2, 'Oops', Instant.now())

        when:
        def json = encoder.encode(build)
        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == build.getClass()
        and:
        copy == build
    }

    def 'should encode and decode ContainerRequestData' () {
        given:
        def encoder = new GsonEncodeStrategy<ContainerRequestData>() { }
        and:
        def data = new ContainerRequestData(1,
                2,
                'ubuntu',
                'from foo',
                new ContainerConfig(entrypoint: ['some','entry'], cmd:['the','cmd']),
                'some/conda/file',
                ContainerPlatform.of('amd64') )
        
        when:
        def json = encoder.encode(data)
        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy == data
    }

    def 'should encode and decide lazy digest store' () {
        given:
        def encoder = new GsonEncodeStrategy<LazyDigestStore>() { }
        and:
        def data = new LazyDigestStore(new DataContentReader('FOO'.bytes.encodeBase64().toString()), 'media', '12345')

        // https://stackoverflow.com/questions/38071530/gson-deserialize-interface-to-its-class-implementation
        when:
        def json = encoder.encode(data)
        and:
        def copy = encoder.decode(json)
        then:
        copy.getClass() == data.getClass()
        and:
        copy == data
    }
}
