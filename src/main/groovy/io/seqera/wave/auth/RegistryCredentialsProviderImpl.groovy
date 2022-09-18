package io.seqera.wave.auth

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.core.ContainerPath
import io.seqera.wave.service.CredentialsService
import io.seqera.wave.service.aws.AwsEcrService
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
    @Value('${wave.registries.docker.username}')
    private String dockerUsername

    @Inject
    @Nullable
    @Value('${wave.registries.docker.password}')
    private String dockerPassword

    @Inject
    @Nullable
    @Value('${wave.registries.quay.username}')
    private String quayUsername

    @Inject
    @Nullable
    @Value('${wave.registries.quay.password}')
    private String quayPassword

    @Inject
    @Nullable
    @Value('${wave.registries.amazon.username}')
    private String awsAccessKey

    @Inject
    @Nullable
    @Value('${wave.registries.amazon.password}')
    private String awsSecretKey

    @Inject
    private RegistryCredentialsFactory credentialsFactory

    @Inject
    @Nullable
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

        if( !registry || registry == 'docker.io' ) {
            if( dockerUsername && dockerPassword ) {
                return credentialsFactory.create('docker.io', dockerUsername, dockerPassword)
            }
        }
        else if( registry == 'quay.io' ) {
            if( quayUsername && quayPassword ) {
                return credentialsFactory.create(registry, quayUsername, quayPassword)
            }
        }
        else if( AwsEcrService.isEcrHost(registry) ) {
            if( awsAccessKey && awsSecretKey ) {
                return credentialsFactory.create(registry, awsAccessKey, awsSecretKey)
            }
        }
        log.debug "Unable to find credentials for registry '$registry'"
        return null
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
     * @return
     *      A {@link RegistryCredentials} object holding the credentials for the specified container or {@code null}
     *      if not credentials can be found
     */
    @Override
    RegistryCredentials getUserCredentials(ContainerPath container, @Nullable Long userId, @Nullable Long workspaceId) {
        // use default credentials for anonymous requests
        if( !userId )
            return getDefaultCredentials(container.registry)
        // use default credentials for default repositories
        if( container.repository==defaultBuildRepository || container.repository==defaultCacheRepository )
            return getDefaultCredentials(container.registry)

        return getUserCredentials0(container.registry, userId, workspaceId)
    }

    protected RegistryCredentials getUserCredentials0(String registry, @Nullable Long userId, @Nullable Long workspaceId) {
        if( !credentialsService ) {
            throw new IllegalStateException("Missing Credentials service -- Make sure the 'tower' micronaut environment has been specified in the Wave configuration environment")
        }

        final keys = credentialsService.findRegistryCreds(registry, userId, workspaceId)
        final result = keys
                ? credentialsFactory.create(registry, keys.userName, keys.password)
                : null as RegistryCredentials
        return result
    }
}
