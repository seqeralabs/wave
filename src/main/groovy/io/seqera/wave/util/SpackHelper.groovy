/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.util


import groovy.transform.CompileStatic
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildFormat

/**
 * Helper class for Spack package manager
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Deprecated
class SpackHelper {

    static String builderDockerTemplate() {
        SpackHelper.class
                .getResourceAsStream('/io/seqera/wave/spack/spack-builder-dockerfile.txt')
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
        else if( buildFormat == BuildFormat.DOCKER ) {
            return builderDockerTemplate() + dockerContent
        }
        else
            throw new IllegalStateException("Unexpected build format: $buildFormat")
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
