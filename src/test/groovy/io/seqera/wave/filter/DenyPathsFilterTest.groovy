package io.seqera.wave.filter

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

@MicronautTest
class DenyPathsFilterTest extends Specification {

    @Inject
    DenyPathsFilter denyPathsFilter

    def "should return true if the path needs to be denied"() {
        given:
        def deniedPaths =['/v2/wt/token1/wave/build/manifest-1',
                                        '/v2/wt/token2/wave/build/manifest-2']
        expect:
        RESULT == denyPathsFilter.isDeniedPath(PATH, deniedPaths)

        where:
        RESULT | PATH
        true   | '/v2/wt/token1/wave/build/manifest-1'
        false  | '/v2/wt/token3/wave/build/manifest-3'
        true   | '/v2/wt/token2/wave/build/manifest-2'
        false  | '/v2/wt/token4/wave/build/manifest-4'
    }
}
