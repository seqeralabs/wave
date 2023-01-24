package io.seqera.wave.service


import spock.lang.Specification

import java.security.PublicKey
import java.util.concurrent.CompletableFuture

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.wave.service.pairing.PairingRecord
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.tower.client.CredentialsDescription
import io.seqera.wave.tower.client.GetCredentialsKeysResponse
import io.seqera.wave.tower.client.ListCredentialsResponse
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ['tower','test'])
class CredentialsServiceTest extends Specification {

    @Inject CredentialsService credentialsService


    @MockBean(TowerClient)
    TowerClient towerClient = Mock(TowerClient)


    @MockBean(PairingService)
    PairingService securityService = Mock(PairingService)

    static AsymmetricCipher TEST_CIPHER = AsymmetricCipher.getInstance()

    def 'should get registry creds'() {
        given: 'a tower user in a workspace on a specific instance with a valid token'
        def userId = 10
        def workspaceId = 10
        def token = "valid-token"
        def towerEndpoint = "http://tower.io:9090"

        and: 'a previously registered key'
        def keypair = TEST_CIPHER.generateKeyPair()
        def keyId = 'generated-key-id'
        def keyRecord = new PairingRecord(
                service: PairingService.TOWER_SERVICE,
                endpoint: towerEndpoint,
                pairingId: keyId,
                privateKey: keypair.private.getEncoded()
        )


        and: 'registry credentials to access a registry stored in tower'
        def credentialsId = 'credentialsId'
        def registryCredentials = '{"userName":"me", "password": "you", "registry": "quay.io"}'
        def credentialsDescription = new CredentialsDescription(
                id: credentialsId,
                provider: 'container-reg',
                registry: 'quay.io'
        )
        and: 'other credentials registered by the user'
        def nonContainerRegistryCredentials = new CredentialsDescription(
                id: 'alt-creds',
                provider: 'azure',
                registry: null
        )
        def otherRegistryCredentials = new CredentialsDescription(
                id: 'docker-creds',
                provider: 'container-reg',
                registry: 'docker.io'
        )


        when: 'look those registry credentials from tower'
        def credentials = credentialsService.findRegistryCreds("quay.io",userId, workspaceId,token,towerEndpoint)

        then: 'the registered key is fetched correctly from the security service'
        1 * securityService.getPairingRecord(PairingService.TOWER_SERVICE,towerEndpoint) >> keyRecord

        and: 'credentials are listed once and return a potential match'
        1 * towerClient.listCredentials(towerEndpoint,token,workspaceId) >> CompletableFuture.completedFuture(new ListCredentialsResponse(
                credentials: [nonContainerRegistryCredentials, credentialsDescription,otherRegistryCredentials]
        ))

        and: 'they match and the encrypted credentials are fetched'
        1 * towerClient.fetchEncryptedCredentials(towerEndpoint,token,credentialsId,keyId,workspaceId) >> CompletableFuture.completedFuture(encryptedCredentialsFromTower(keypair.public,registryCredentials))

        and:
        credentials.userName == 'me'
        credentials.password == "you"
        credentials.registry == "quay.io"
        noExceptionThrown()
    }


    def 'should fail if keys where not registered for the tower endpoint'() {
        when:
        credentialsService.findRegistryCreds('quay.io',10,10,"token",'endpoint')

        then: 'the security service does not have the key for the hostname'
        1 * securityService.getPairingRecord(PairingService.TOWER_SERVICE,'endpoint') >> null

        and:
        thrown(IllegalStateException)
    }

    def 'should return no registry credentials if the user has no credentials in tower' () {
        when:
        def credentials = credentialsService.findRegistryCreds('quay.io', 10, 10, "token",'tower.io')
        then: 'a key is found'
        1 * securityService.getPairingRecord(PairingService.TOWER_SERVICE, 'tower.io') >> new PairingRecord(
                pairingId: 'a-key-id',
                service: PairingService.TOWER_SERVICE,
                endpoint: 'tower.io',
                privateKey: new byte[0], // we don't care about the value of the key
        )
        and: 'credentials are listed but are empty'
        1 * towerClient.listCredentials('tower.io',"token",10) >> CompletableFuture.completedFuture(new ListCredentialsResponse(credentials: []))

        and: 'no registry credentials are returned'
        credentials == null
    }

    def 'should return no registry credentials if none match'() {
        given: 'some non matching credentials'
        def nonContainerRegistryCredentials = new CredentialsDescription(
                id: 'alt-creds',
                provider: 'azure',
                registry: null
        )
        def otherRegistryCredentials = new CredentialsDescription(
                id: 'docker-creds',
                provider: 'container-reg',
                registry: 'docker.io'
        )

        when:
        def credentials = credentialsService.findRegistryCreds('quay.io', 10, 10, "token",'tower.io')

        then: 'a key is found'
        1 * securityService.getPairingRecord(PairingService.TOWER_SERVICE, 'tower.io') >> new PairingRecord(
                pairingId: 'a-key-id',
                service: PairingService.TOWER_SERVICE,
                endpoint: 'tower.io',
                privateKey: new byte[0], // we don't care about the value of the key
        )

        and: 'non matching credentials are listed'
        1 * towerClient.listCredentials('tower.io',"token",10) >> CompletableFuture.completedFuture(new ListCredentialsResponse(
                credentials: [nonContainerRegistryCredentials,otherRegistryCredentials]
        ))

        then:
        credentials == null
    }


    def 'should parse credentials payload' () {
        given:
        def svc = new CredentialServiceImpl()

        when:
        def keys = svc.parsePayload('{"registry":"foo.io", "userName":"me", "password": "you"}')
        then:
        keys.registry == 'foo.io'
        keys.userName == 'me'
        keys.password == 'you'
    }


    private static GetCredentialsKeysResponse encryptedCredentialsFromTower(PublicKey key, String credentials) {
        return new GetCredentialsKeysResponse(keys:  TEST_CIPHER.encrypt(key,credentials.getBytes()).encode())
    }


}
