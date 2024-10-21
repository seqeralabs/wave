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

package io.seqera.wave.auth


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.core.ContainerPath
import io.seqera.wave.service.CredentialsService
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements a basic credentials provider fetching the registry creds
 * from the application config file
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class RegistryCredentialsProviderImpl implements RegistryCredentialsProvider {

    @Inject
    @Nullable
    private RegistryConfig registryConfig

    @Inject
    private RegistryCredentialsFactory credentialsFactory

    @Inject
    private CredentialsService credentialsService

    @Inject
    private BuildConfig buildConfig

    /**
     * Find the corresponding credentials for the specified registry
     *
     * @param registry
     *      A registry name e.g. docker.io or quay.io
     * @return
     *      A {@link RegistryCredentials} object holding the credentials for the specified registry or {@code null}
     *      if not credentials can be found
     */
    @Override
    RegistryCredentials getDefaultCredentials(String registry) {
        return getDefaultCredentials0(registry)
    }

    @Override
    RegistryCredentials getDefaultCredentials(ContainerPath container) {
        return getDefaultCredentials0(container?.registry)
    }

    protected RegistryCredentials getDefaultCredentials0(String registry) {
        final config = registryConfig?.getRegistryKeys(registry)
        if( !config ){
            log.debug "Unable to find default credentials for registry '$registry'"
            return null
        }
        return credentialsFactory.create(registry, config.username, config.password)
    }

    protected RegistryCredentials getDefaultRepoCredentials0(ContainerPath container) {
        final config = registryConfig?.getRegistryKeys(container.repository)
        if( !config ){
            log.debug "Unable to find default credentials for repository '$container.repository'"
            return null
        }
        return credentialsFactory.create(container.registry, config.username, config.password)
    }

    /**
     * Provides the credentials for the specified container associated with the user and tower
     * workspace specified.
     *
     * @param container
     *      A container name e.g. docker.io/library/ubuntu.
     * @param userId
     *      The tower user Id.
     * @param workspaceId
     *      The tower workspace Id.
     * @param towerToken
     *      The user personal access token to access the Tower services
     * @param towerEndpoint
     *      The Tower service endpoint to be used to retrieve container repositories credentials
     * @return
     *      A {@link RegistryCredentials} object holding the credentials for the specified container or {@code null}
     *      if not credentials can be found
     */
    @Override
    RegistryCredentials getUserCredentials(ContainerPath container, PlatformId identity) {
        if( !identity )
            throw new IllegalArgumentException("Missing required parameter userId -- Unable to retrieve credentials for container repository '$container'")

        // use default credentials for default repositories
        // NOTE: this requires that 'defaultBuildRepository', 'defaultCacheRepository' and 'defaultCommunityRegistry' have a unique registry host name
        // that means that for example docker.io/some/repo should not be used otherwise wave credentials could be used in place of user credentials
        // for a repo having the same registry host
        if( container.sameRegistry(buildConfig.defaultBuildRepository) || container.sameRegistry(buildConfig.defaultCacheRepository) || container.sameRegistry(buildConfig.defaultCommunityRegistry) )
            return getDefaultCredentials(container)

        return getUserCredentials0(container.registry, identity)
    }

    protected RegistryCredentials getUserCredentials0(String registry, PlatformId identity) {
        final keys = credentialsService.findRegistryCreds(registry, identity)
        final result = keys
                ? credentialsFactory.create(registry, keys.userName, keys.password)
                // create a missing credentials class with a unique key (the access token) because even when
                // no credentials are provided a registry auth token token can be associated to this user
                : new MissingCredentials(identity.accessToken)
        return result
    }
}
