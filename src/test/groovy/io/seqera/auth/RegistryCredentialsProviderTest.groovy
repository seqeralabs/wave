package io.seqera.auth

import spock.lang.Specification

import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryCredentialsProviderTest extends Specification {

    @Inject
    RegistryCredentialsProviderImpl credentialsProvider

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
    
    def 'should find docker creds' () {
        expect:
        credentialsProvider.getCredentials(null) == new BasicRegistryCredentials(dockerUsername, dockerPassword)
        credentialsProvider.getCredentials('docker.io') == new BasicRegistryCredentials(dockerUsername, dockerPassword)
    }

    def 'should find quay creds' () {
        expect:
        credentialsProvider.getCredentials('quay.io') == new BasicRegistryCredentials(quayUsername, quayPassword)
    }

    def 'should not find creds' () {
        expect:
        credentialsProvider.getCredentials('foo') == null
    }

}
