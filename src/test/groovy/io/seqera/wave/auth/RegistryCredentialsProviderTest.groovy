package io.seqera.wave.auth


import spock.lang.Requires
import spock.lang.Specification

import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.ContainerRegistryKeys
import io.seqera.wave.service.CredentialsService
import io.seqera.wave.service.aws.AwsEcrService
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
    @Value('${wave.registries.docker.io.username}')
    private String dockerUsername

    @Inject
    @Nullable
    @Value('${wave.registries.docker.io.password}')
    private String dockerPassword

    @Inject
    @Nullable
    @Value('${wave.registries.quay.io.username}')
    private String quayUsername

    @Inject
    @Nullable
    @Value('${wave.registries.quay.io.password}')
    private String quayPassword
    
    def 'should find docker creds' () {
        when:
        def creds1 = credentialsProvider.getDefaultCredentials(null)
        then:
        creds1.username == dockerUsername
        creds1.password == dockerPassword

        when:
        def creds2 = credentialsProvider.getDefaultCredentials('docker.io')
        then:
        creds2.username == dockerUsername
        creds2.password == dockerPassword
    }

    def 'should find quay creds' () {
        when:
        def creds = credentialsProvider.getDefaultCredentials('quay.io')
        then:
        creds.username == quayUsername
        creds.password == quayPassword
    }

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should get ecr registry creds' () {
        given:
        def reg = '195996028523.dkr.ecr.eu-west-1.amazonaws.com'
        when:
        def creds = credentialsProvider.getDefaultCredentials(reg)
        then:
        creds.username == 'AWS'
        creds.password.size() > 0
    }

    def 'should not find creds' () {
        expect:
        credentialsProvider.getDefaultCredentials('foo') == null
    }

    def 'should get credentials from user' () {
        given:
        def REGISTRY = 'foo'
        def USER_ID = 100
        def WORKSPACE_ID = 200
        def TOWER_TOKEN = "token"
        def TOWER_ENDPOINT = "localhost:8080"
        and:
        def credentialService = Mock(CredentialsService)
        def credentialsFactory = new RegistryCredentialsFactoryImpl(awsEcrService: Mock(AwsEcrService))
        def provider = Spy(new RegistryCredentialsProviderImpl(credentialsFactory: credentialsFactory, credentialsService: credentialService))

        when:
        def result = provider.getUserCredentials0(REGISTRY, USER_ID, WORKSPACE_ID, TOWER_TOKEN, TOWER_ENDPOINT)
        then:
        1 * credentialService.findRegistryCreds(REGISTRY, USER_ID, WORKSPACE_ID, TOWER_TOKEN, TOWER_ENDPOINT) >> new ContainerRegistryKeys(userName:'usr1',password:'pwd2',registry:REGISTRY)
        and:
        result.getUsername() == 'usr1'
        result.getPassword() == 'pwd2'
    }
}
