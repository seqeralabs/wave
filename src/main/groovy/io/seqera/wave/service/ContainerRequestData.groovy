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

package io.seqera.wave.service

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.tower.PlatformId
import static io.seqera.wave.util.StringUtils.trunc
/**
 * Model a container request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ContainerRequestData {

    final PlatformId identity
    final String containerImage
    final String containerFile
    final ContainerConfig containerConfig
    final String condaFile
    final ContainerPlatform platform
    final String buildId
    final Boolean buildNew
    final Boolean freeze
    final Boolean mirror

    boolean durable() {
        return freeze || mirror
    }

    PlatformId getIdentity() {
        return identity
    }

    ContainerCoordinates coordinates() { ContainerCoordinates.parse(containerImage) }

    @Override
    String toString() {
        return "ContainerRequestData[identity=${getIdentity()}; containerImage=$containerImage; containerFile=${trunc(containerFile)}; condaFile=${trunc(condaFile)}; containerConfig=${containerConfig}]"
    }

}
