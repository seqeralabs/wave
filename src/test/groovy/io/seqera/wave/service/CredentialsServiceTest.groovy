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

package io.seqera.wave.service


import spock.lang.Specification

import java.security.PublicKey
import java.time.Duration
import java.time.Instant
import java.util.concurrent.CompletableFuture

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.wave.service.pairing.PairingRecord
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth
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
                privateKey: keypair.getPrivate().getEncoded(),
                expiration: (Instant.now() + Duration.ofSeconds(10)) )


        and: 'registry credentials to access a registry stored in tower'
        def credentialsId = 'credentialsId'
        def registryCredentials = '{"userName":"me", "password": "you", "registry": "quay.io"}'
        def credentialsDescription = new CredentialsDescription(
                id: credentialsId,
                provider: 'container-reg',
                registry: 'quay.io' )
        and: 'other credentials registered by the user'
        def nonContainerRegistryCredentials = new CredentialsDescription(
                id: 'alt-creds',
                provider: 'azure',
                registry: null )
        and:
        def otherRegistryCredentials = new CredentialsDescription(
                id: 'docker-creds',
                provider: 'container-reg',
                registry: 'docker.io' )
        and:
        def identity = new PlatformId(new User(id:userId), workspaceId,token,towerEndpoint)
        def auth = JwtAuth.of(identity)

        when: 'look those registry credentials from tower'
        def credentials = credentialsService.findRegistryCreds("quay.io",identity)

        then: 'the registered key is fetched correctly from the security service'
        1 * securityService.getPairingRecord(PairingService.TOWER_SERVICE, towerEndpoint) >> keyRecord

        and: 'credentials are listed once and return a potential match'
        1 * towerClient.listCredentials(towerEndpoint,auth,workspaceId) >> CompletableFuture.completedFuture(new ListCredentialsResponse(
                credentials: [nonContainerRegistryCredentials, credentialsDescription,otherRegistryCredentials]
        ))

        and: 'they match and the encrypted credentials are fetched'
        1 * towerClient.fetchEncryptedCredentials(towerEndpoint, auth, credentialsId, keyId, workspaceId) >> CompletableFuture.completedFuture(encryptedCredentialsFromTower(keypair.getPublic(), registryCredentials))

        and:
        credentials.userName == 'me'
        credentials.password == "you"
        credentials.registry == "quay.io"
        noExceptionThrown()
    }


    def 'should fail if keys where not registered for the tower endpoint'() {
        given:
        def identity = new PlatformId(new User(id:10), 10,"token",'endpoint')
        when:
        credentialsService.findRegistryCreds('quay.io',identity)

        then: 'the security service does not have the key for the hostname'
        1 * securityService.getPairingRecord(PairingService.TOWER_SERVICE,'endpoint') >> null

        and:
        thrown(IllegalStateException)
    }

    def 'should return no registry credentials if the user has no credentials in tower' () {
        given:
        def identity = new PlatformId(new User(id:10), 10,"token",'tower.io')
        def auth = JwtAuth.of(identity)
        when:
        def credentials = credentialsService.findRegistryCreds('quay.io', identity)
        then: 'a key is found'
        1 * securityService.getPairingRecord(PairingService.TOWER_SERVICE, 'tower.io') >> new PairingRecord(
                pairingId: 'a-key-id',
                service: PairingService.TOWER_SERVICE,
                endpoint: 'tower.io',
                privateKey: new byte[0], // we don't care about the value of the key
                expiration: Instant.now() + Duration.ofSeconds(5)
        )
        and: 'credentials are listed but are empty'
        1 * towerClient.listCredentials('tower.io',auth,10) >> CompletableFuture.completedFuture(new ListCredentialsResponse(credentials: []))

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
        and:
        def identity = new PlatformId(new User(id:10), 10,"token",'tower.io')
        def auth = JwtAuth.of(identity)

        when:
        def credentials = credentialsService.findRegistryCreds('quay.io', identity)

        then: 'a key is found'
        1 * securityService.getPairingRecord(PairingService.TOWER_SERVICE, 'tower.io') >> new PairingRecord(
                pairingId: 'a-key-id',
                service: PairingService.TOWER_SERVICE,
                endpoint: 'tower.io',
                privateKey: new byte[0], // we don't care about the value of the key
                expiration: Instant.now() + Duration.ofSeconds(5)
        )

        and: 'non matching credentials are listed'
        1 * towerClient.listCredentials('tower.io',auth,10) >> CompletableFuture.completedFuture(new ListCredentialsResponse(
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
        return new GetCredentialsKeysResponse(keys: TEST_CIPHER.encrypt(key,credentials.getBytes()).encode())
    }

}
