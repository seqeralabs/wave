/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

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
