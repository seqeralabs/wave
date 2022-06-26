package io.seqera.auth

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
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


    @Override
    RegistryCredentials getCredentials(String registry) {
        if( !registry || registry == 'docker.io' ) {
            if( dockerUsername && dockerPassword ) {
                return new SimpleRegistryCredentials(dockerUsername, dockerPassword)
            }
        }
        else if( registry == 'quay.io' ) {
            if( quayUsername && quayPassword ) {
                return new SimpleRegistryCredentials(quayUsername, quayPassword)
            }
        }
        log.debug "Unable to find credentials for registry '$registry'"
        return null
    }
}
