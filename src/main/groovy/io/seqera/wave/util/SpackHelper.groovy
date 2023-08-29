package io.seqera.wave.util

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildFormat

/**
 * Helper class for Spack package manager
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SpackHelper {

    static String builderDockerTemplate() {
        SpackHelper.class
                .getResourceAsStream('/io/seqera/wave/spack/spack-builder-containerfile.txt')
                .getText()
    }

    static String builderSingularityTemplate() {
        SpackHelper.class
                .getResourceAsStream('/io/seqera/wave/spack/spack-builder-singularityfile.txt')
                .getText()
    }
    static String prependBuilderTemplate(String dockerContent, BuildFormat buildFormat) {
        if(buildFormat == BuildFormat.SINGULARITY){
            return builderSingularityTemplate() + dockerContent
        }
        return builderDockerTemplate() + dockerContent
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
