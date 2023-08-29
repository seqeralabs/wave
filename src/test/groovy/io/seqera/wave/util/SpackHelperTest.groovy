package io.seqera.wave.util

import spock.lang.Specification

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildFormat

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SpackHelperTest extends Specification {

    def 'should load builder template' () {
        expect:
        SpackHelper.builderDockerTemplate().startsWith('# Builder image')
    }

    def 'should prepend builder template' () {
        expect:
        SpackHelper.prependBuilderTemplate('foo', BuildFormat.DOCKER).startsWith('# Builder image')
        SpackHelper.prependBuilderTemplate('foo', BuildFormat.SINGULARITY).endsWith('\nfoo')
    }

    def 'should map platform to spack arch' () {
        expect:
        SpackHelper.toSpackArch(ContainerPlatform.of('x86_64'))            == 'x86_64'
        SpackHelper.toSpackArch(ContainerPlatform.of('linux/x86_64'))       == 'x86_64'
        SpackHelper.toSpackArch(ContainerPlatform.of('amd64'))             == 'x86_64'
        SpackHelper.toSpackArch(ContainerPlatform.of('aarch64'))           == 'aarch64'
        SpackHelper.toSpackArch(ContainerPlatform.of('arm64'))             == 'aarch64'
        SpackHelper.toSpackArch(ContainerPlatform.of('linux/arm64/v8'))    == 'aarch64'

        when:
        SpackHelper.toSpackArch(ContainerPlatform.of('linux/arm64/v7'))
        then:
        thrown(IllegalArgumentException)
    }
}
