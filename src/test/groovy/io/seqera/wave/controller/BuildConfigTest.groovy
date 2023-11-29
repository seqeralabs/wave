package io.seqera.wave.controller

import spock.lang.Specification

import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.core.ContainerPlatform

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildConfigTest extends Specification {

    def 'should return singularity image' () {
        given:
        BuildConfig config

        when:
        config = new BuildConfig(singularityImage: 'foo')
        then:
        config.singularityImage == 'foo'
        config.singularityImageArm64 == 'foo-arm64'
        and:
        config.getSingularityImage( ContainerPlatform.of('amd64') ) == 'foo'
        config.getSingularityImage( ContainerPlatform.of('arm64') ) == 'foo-arm64'

        when:
        config = new BuildConfig(singularityImage: 'foo', singularityImageArm64: 'bar')
        then:
        config.singularityImage == 'foo'
        config.singularityImageArm64 == 'bar'
        and:
        config.getSingularityImage( ContainerPlatform.of('amd64') ) == 'foo'
        config.getSingularityImage( ContainerPlatform.of('arm64') ) == 'bar'
    }

}
