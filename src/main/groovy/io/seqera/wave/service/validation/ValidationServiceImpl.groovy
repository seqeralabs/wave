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
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.model.ContainerCoordinates
import io.seqera.wave.util.StringUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Validation service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class ValidationServiceImpl implements ValidationService {

    enum RepoType { Build, Cache, Mirror }

    static private final List<String> VALID_PROTOCOLS = ['http','https']

    @Inject
    private BuildConfig buildConfig

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
    String checkBuildRepository(String repo, RepoType type ) {
        if( !repo && type!=RepoType.Mirror )
            return null
        // repo is required when using mirror more
        if( !repo && type==RepoType.Mirror )
            return "Missing target build repository required by 'mirror' mode"
        final typeStr = type==RepoType.Cache ? "build cache" : "build"
        // check does not start with a protocol prefix
        final prot = StringUtils.getUrlProtocol(repo)
        if( prot )
            return "Container ${typeStr} repository should not include any protocol prefix - offending value: '$repo'"
        // check no tag is included
        final coords = ContainerCoordinates.parse(repo)
        if( !coords.repository && type!=RepoType.Mirror )
            return "Container ${typeStr} repository is invalid or incomplete - offending value: '$repo'"
        if( coords.reference && repo.endsWith(":${coords.reference}") )
            return "Container ${typeStr} repository should not include any tag suffix - offending value: '$repo'"
        if( type==RepoType.Mirror && !isCustomRepo(coords.registry) )
            return "Mirror registry not allowed - offending value '${repo}'"
        else
            return null
    }

    boolean isCustomRepo(String repo) {
        if( !repo )
            return false
        if( buildConfig.defaultCommunityRegistry && repo.startsWith(buildConfig.defaultCommunityRegistry) )
            return false
        if( buildConfig.defaultBuildRepository && repo.startsWith(buildConfig.defaultBuildRepository) )
            return false
        if( buildConfig.defaultCacheRepository && repo.startsWith(buildConfig.defaultCacheRepository) )
            return false
        return true
    }

}
