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

package io.seqera.wave.service.validation

import groovy.transform.CompileStatic
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.util.StringUtils
import jakarta.inject.Singleton
/**
 * Validation service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class ValidationServiceImpl implements ValidationService {

    static private final List<String> VALID_PROTOCOLS = ['http','https']

    @Override
    String checkEndpoint(String endpoint) {
        final scheme = StringUtils.getUrlProtocol(endpoint)
        if( !scheme ) {
            return "Missing endpoint protocol — offending value: $endpoint"
        }

        if( scheme.toLowerCase() !in VALID_PROTOCOLS ) {
            return "Invalid endpoint protocol — offending value: $endpoint"
        }

        try {
            new URI(endpoint)
        }
        catch (URISyntaxException e) {
            return "Invalid endpoint '${endpoint}' — cause: ${e.message}"
        }

        return null
    }

    @Override
    String checkContainerName(String name) {
        if( !name )
            return null
        // check does not start with a protocol prefix
        final prot = StringUtils.getUrlProtocol(name)
        if( prot ) {
            return "Invalid container repository name — offending value: $name"
        }

        try {
            ContainerCoordinates.parse(name)
        }
        catch (IllegalArgumentException e) {
            return "Invalid container image name — offending value: $name"
        }
        return null
    }

    @Override
    String checkBuildRepository(String repo, boolean cache) {
        if( !repo )
            return null
        final type = cache ? "build cache" : "build"
        // check does not start with a protocol prefix
        final prot = StringUtils.getUrlProtocol(repo)
        if( prot )
            return "Container ${type} repository should not include any protocol prefix - offending value: $repo"
        // check no tag is included
        final coords = ContainerCoordinates.parse(repo)
        if( !coords.repository )
            return "Container ${type} repository is invalid or incomplete - offending value: $repo"
        if( coords.reference && repo.endsWith(":${coords.reference}") )
            return "Container ${type} repository should not include any tag suffix - offending value: $repo"
        else
            return null
    }

}
