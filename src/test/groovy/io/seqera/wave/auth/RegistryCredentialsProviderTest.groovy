package io.seqera.wave.auth

import spock.lang.Requires
import spock.lang.Specification

import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryCredentialsProviderImpl
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
        when:
        def creds1 = credentialsProvider.getCredentials(null)
        then:
        creds1.username == dockerUsername
        creds1.password == dockerPassword

        when:
        def creds2 = credentialsProvider.getCredentials('docker.io')
        then:
        creds2.username == dockerUsername
        creds2.password == dockerPassword
    }

    def 'should find quay creds' () {
        when:
        def creds = credentialsProvider.getCredentials('quay.io')
        then:
        creds.username == quayUsername
        creds.password == quayPassword
    }

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should get ecr registry creds' () {
        given:
        def reg = '195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        when:
        def creds = credentialsProvider.getCredentials(reg)
        then:
        creds.username == 'AWS'
        creds.password.size() > 0
    }

    def 'should not find creds' () {
        expect:
        credentialsProvider.getCredentials('foo') == null
    }

}
