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

package io.seqera.wave.service.builder

import io.seqera.wave.api.BuildContext
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.util.RegHelper

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerIdServiceImpl implements ContainerIdService{

    @Override
    String id(String containerFile, String condaFile, String spackFile, ContainerPlatform platform, String repository, BuildContext buildContext) {
        return computeDigest(containerFile, containerFile, spackFile, platform, repository, buildContext)
    }

    static private String computeDigest(String containerFile, String condaFile, String spackFile, ContainerPlatform platform, String repository, BuildContext buildContext) {
        final attrs = new LinkedHashMap<String,String>(10)
        attrs.containerFile = containerFile
        attrs.condaFile = condaFile
        attrs.platform = platform?.toString()
        attrs.repository = repository
        if( spackFile ) attrs.spackFile = spackFile
        if( buildContext ) attrs.buildContext = buildContext.tarDigest
        return RegHelper.sipHash(attrs)
    }

}
