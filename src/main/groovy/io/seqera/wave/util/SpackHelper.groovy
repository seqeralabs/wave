package io.seqera.wave.util

import io.seqera.wave.core.ContainerPlatform

/**
 * Helper class for Spack package manager
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SpackHelper {

    static String builderTemplate() {
        SpackHelper.class
                .getResourceAsStream('/io/seqera/wave/spack/spack-builder-containerfile.txt')
                .getText()
    }

    static String prependBuilderTemplate(String dockerContent) {
        return builderTemplate() + dockerContent
    }

    static String toSpackArch(ContainerPlatform platform) {
        if( !platform )
            throw new IllegalArgumentException("Missing container platform argument")
        final value = platform.toString()
        if( value=='linux/amd64' )
            return 'x86_64'
        if( value=='linux/arm64' )
            return 'aarch64'
        throw new IllegalArgumentException("Unable to map container platform '${platform}' to Spack architecture")
    }

}
