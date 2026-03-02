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

package io.seqera.wave.core

import groovy.transform.CompileStatic
/**
 * A composite container platform representing multiple architectures.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class MultiContainerPlatform extends ContainerPlatform {

    static final MultiContainerPlatform MULTI_PLATFORM = new MultiContainerPlatform(
            [ContainerPlatform.of('linux/amd64'), ContainerPlatform.of('linux/arm64')]
    )

    final List<ContainerPlatform> platforms

    MultiContainerPlatform(List<ContainerPlatform> platforms) {
        super(platforms[0].os, platforms[0].arch, platforms[0].variant)
        this.platforms = List.copyOf(platforms)
    }

    @Override
    String toString() {
        platforms.collect { it.toString() }.join(',')
    }
}
