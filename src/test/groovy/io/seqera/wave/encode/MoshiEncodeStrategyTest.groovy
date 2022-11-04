package io.seqera.wave.encode

import spock.lang.Specification

import java.time.Instant

import com.squareup.moshi.Moshi
import io.seqera.wave.service.builder.BuildResult

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class MoshiEncodeStrategyTest extends Specification {

    def 'should encode and decode build result' () {
        given:
        def moshi = new Moshi.Builder()
                .add(new DateTimeAdapter())
                .build()
        def jsonAdapter = moshi.adapter(BuildResult.class);
        and:
        def build = BuildResult.completed('1', 2, 'Oops', Instant.now())

        when:
        def json = jsonAdapter.toJson(build)
        and:
        def copy = jsonAdapter.fromJson(json)
        then:
        copy.getClass() == build.getClass()
        and:
        copy == build
    }

}
