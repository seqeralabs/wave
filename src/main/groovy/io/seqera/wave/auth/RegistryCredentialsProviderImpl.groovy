package io.seqera.wave.auth

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.core.ContainerPath
import io.seqera.wave.service.CredentialsService
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
    private RegistryConfig registryConfigurationFactory

    @Inject
    private RegistryCredentialsFactory credentialsFactory

    @Inject
    private CredentialsService credentialsService

    @Value('${wave.build.repo}')
    private String defaultBuildRepository

    @Value('${wave.build.cache}')
    private String defaultCacheRepository

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

    protected RegistryCredentials getDefaultCredentials0(String registry) {

        final config = registryConfigurationFactory.getRegistryKeys(registry)
        if( !config ){
            log.debug "Unable to find credentials for registry '$registry'"
            return null
        }
        return credentialsFactory.create(registry, config.username, config.password)
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
    RegistryCredentials getUserCredentials(ContainerPath container, Long userId, Long workspaceId, String towerToken, String towerEndpoint) {
        if( !userId )
            throw new IllegalArgumentException("Missing required parameter userId -- Unable to retrieve credentials for container repository '$container'")

        // use default credentials for default repositories
        if( container.repository==defaultBuildRepository || container.repository==defaultCacheRepository )
            return getDefaultCredentials(container.registry)

        return getUserCredentials0(container.registry, userId, workspaceId, towerToken, towerEndpoint)
    }

    protected RegistryCredentials getUserCredentials0(String registry, Long userId, Long workspaceId, String towerToken, String towerEndpoint) {
        final keys = credentialsService.findRegistryCreds(registry, userId, workspaceId, towerToken, towerEndpoint)
        final result = keys
                ? credentialsFactory.create(registry, keys.userName, keys.password)
                // create a missing credentials class with a unique key (the access token) because even when
                // no credentials are provided a registry auth token token can be associated to this user
                : new MissingCredentials(towerToken)
        return result
    }
}
