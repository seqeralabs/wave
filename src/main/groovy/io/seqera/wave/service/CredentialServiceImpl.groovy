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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.tower.crypto.EncryptedPacket
import io.seqera.wave.service.aws.AwsEcrService
import io.seqera.service.pairing.PairingService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.CredentialsDescription
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.WaveDefault.DOCKER_IO
/**
 * Define operations to access container registry credentials from Tower
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Singleton
class CredentialServiceImpl implements CredentialsService {

    @Inject
    private TowerClient towerClient

    @Inject
    private PairingService keyService

    @Override
    ContainerRegistryKeys findRegistryCreds(String repository, PlatformId identity) {
        if (!identity.userId)
            throw new IllegalArgumentException("Missing userId parameter")
        if (!identity.accessToken)
            throw new IllegalArgumentException("Missing Tower access token")

        final pairing = keyService.getPairingRecord(PairingService.TOWER_SERVICE, identity.towerEndpoint)
        if (!pairing)
            throw new IllegalStateException("No exchange key registered for service ${PairingService.TOWER_SERVICE} at endpoint: ${identity.towerEndpoint}")
        if (pairing.isExpired())
            log.debug("Exchange key registered for service ${PairingService.TOWER_SERVICE} at endpoint: ${identity.towerEndpoint} used after expiration, should be renewed soon")

        final all = towerClient.listCredentials(identity.towerEndpoint, JwtAuth.of(identity), identity.workspaceId, identity.workflowId).credentials

        if (!all) {
            log.debug "No credentials found for userId=$identity.userId; workspaceId=$identity.workspaceId; endpoint=$identity.towerEndpoint"
            return null
        }

        final target = repository ?: DOCKER_IO
        final registryName = target.tokenize('/')[0]
        def creds = findMostSpecificCreds(all, target)
        if (!creds && identity.workflowId && AwsEcrService.isEcrHost(registryName) ) {
            creds = findComputeCreds(identity)
        }
        if (!creds) {
            log.debug "No credentials matching criteria repository=$target; userId=$identity.userId; workspaceId=$identity.workspaceId; workflowId=${identity.workflowId}; endpoint=$identity.towerEndpoint"
            return null
        }

        // log for debugging purposes
        log.debug "Credentials matching criteria repository=$target; userId=$identity.userId; workspaceId=$identity.workspaceId; endpoint=$identity.towerEndpoint => $creds"
        // now fetch the encrypted key
        final encryptedCredentials = towerClient.fetchEncryptedCredentials(identity.towerEndpoint, JwtAuth.of(identity), creds.id, pairing.pairingId, identity.workspaceId, identity.workflowId)
        final privateKey = pairing.privateKey
        final credentials = decryptCredentials(privateKey, encryptedCredentials.keys)
        return parsePayload(credentials)
    }

    /**
     * Find the container registry credentials whose {@code registry} is the longest prefix of the target repository.
     * A {@code registry} can be a bare host (e.g. {@code quay.io}) or a host followed by a path
     * (e.g. {@code quay.io/org}), which allows different credentials for different repositories on the same host.
     * When more credentials match with the same length, the first one wins.
     *
     * @param all The credentials available to the user
     * @param repository The target repository e.g. {@code quay.io/org/image}
     * @return The best matching credentials or {@code null} when none match
     */
    static protected CredentialsDescription findMostSpecificCreds(List<CredentialsDescription> all, String repository) {
        return all
                .findAll { it.provider == 'container-reg' && isPrefixOf(it.registry ?: DOCKER_IO, repository) }
                .max { (it.registry ?: DOCKER_IO).length() }
    }

    static private boolean isPrefixOf(String registry, String repository) {
        return repository == registry || repository.startsWith(registry + '/')
    }

    CredentialsDescription findComputeCreds(PlatformId identity) {
        try {
            return findComputeCreds0(identity)
        }
        catch (Exception e) {
            log.error("Unable to retrieve Platform launch credentials for $identity - cause ${e.message}")
            return null
        }
    }

    protected CredentialsDescription findComputeCreds0(PlatformId identity) {
        final response = towerClient.describeWorkflowLaunch(identity.towerEndpoint, JwtAuth.of(identity), identity.workspaceId, identity.workflowId)
        if( !response )
            return null
        final computeEnv = response?.launch?.computeEnv
        if( !computeEnv )
            return null
        if( computeEnv.platform != 'aws-batch' )
            return null
        return new CredentialsDescription(id: computeEnv.credentialsId, provider: 'aws')
    }

    protected String decryptCredentials(byte[] encodedKey, String payload) {
        final packet = EncryptedPacket.decode(payload)
        final cipher = AsymmetricCipher.getInstance()
        final privateKey = cipher.decodePrivateKey(encodedKey)
        final data = cipher.decrypt(packet, privateKey)
        return new String(data)
    }

    protected ContainerRegistryKeys parsePayload(String json) {
        try {
            return ContainerRegistryKeys.fromJson(json)
        }
        catch (Exception e) {
            log.debug "Unable to parse container keys: $json", e
            return null
        }
    }

}
