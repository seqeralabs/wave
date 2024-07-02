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
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.client.CredentialsDescription
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.TowerClient
import io.seqera.wave.tower.compute.ComputeEnv
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
    ContainerRegistryKeys findRegistryCreds(String registryName, PlatformId identity) {
        if (!identity.userId)
            throw new IllegalArgumentException("Missing userId parameter")
        if (!identity.accessToken)
            throw new IllegalArgumentException("Missing Tower access token")

        final pairing = keyService.getPairingRecord(PairingService.TOWER_SERVICE, identity.towerEndpoint)
        if (!pairing)
            throw new IllegalStateException("No exchange key registered for service ${PairingService.TOWER_SERVICE} at endpoint: ${identity.towerEndpoint}")
        if (pairing.isExpired())
            log.debug("Exchange key registered for service ${PairingService.TOWER_SERVICE} at endpoint: ${identity.towerEndpoint} used after expiration, should be renewed soon")

        final all = towerClient.listCredentials(identity.towerEndpoint, JwtAuth.of(identity), identity.workspaceId).get().credentials

        if (!all) {
            log.debug "No credentials found for userId=$identity.userId; workspaceId=$identity.workspaceId; endpoint=$identity.towerEndpoint"
            return null
        }

        // find credentials with a matching registry
        // TODO @t0randr
        //  for the time being we take the first matching credentials.
        //  A better approach would be to match the credentials
        //  based on user repository, but this is not supported by tower:
        //  For instance if we try to pull from docker.io/seqera/tower:v22
        //  then we should match credentials for docker.io/seqera instead of
        //  the ones associated with docker.io.
        //  This cannot be implemented at the moment since, in tower, container registry
        //  credentials are associated to the whole registry
        final matchingRegistryName = registryName ?: DOCKER_IO
        def creds = all.find {
            it.provider == 'container-reg'  && (it.registry ?: DOCKER_IO) == matchingRegistryName
        }
        if (!creds) {
            log.debug "No credentials matching criteria registryName=$registryName; userId=${identity.user.id}; workspaceId=${identity.workspaceId}; endpoint=${identity.towerEndpoint}"
            //when there are no registry credential, try fetching them from compute credentials
            creds = findComputeCreds(identity)
            if (!creds) {
                log.debug "No compute credentials found for workflowId=${identity.workflowId}; userId=${identity.userId}; workspaceId=${identity.workspaceId}; endpoint=${identity.towerEndpoint}"
                return null
            }
        }

        // log for debugging purposes
        log.debug "Credentials matching criteria registryName=$registryName; userId=$identity.userId; workspaceId=$identity.workspaceId; endpoint=$identity.towerEndpoint => $creds"
        // now fetch the encrypted key
        final encryptedCredentials = towerClient.fetchEncryptedCredentials(identity.towerEndpoint, JwtAuth.of(identity), creds.id, pairing.pairingId, identity.workspaceId).get()
        final privateKey = pairing.privateKey
        final credentials = decryptCredentials(privateKey, encryptedCredentials.keys)
        return parsePayload(credentials)
    }

    CredentialsDescription findComputeCreds(PlatformId identity) {
        final describeWorkflowLaunchResponse = towerClient.fetchWorkflowLaunchInfo(identity.towerEndpoint, JwtAuth.of(identity), identity.workflowId)
        if(describeWorkflowLaunchResponse) {
            ComputeEnv computeEnv = describeWorkflowLaunchResponse.get()?.launch?.computeEnv
            if (computeEnv && (computeEnv.platform == 'aws-batch' || computeEnv.platform == 'google-batch')) {
                return new CredentialsDescription(
                        id: computeEnv.credentialsId
                )
            }
        }
        return null
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
