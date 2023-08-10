package io.seqera.wave.service


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.tower.crypto.EncryptedPacket
import io.seqera.wave.service.pairing.PairingService
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
    ContainerRegistryKeys findRegistryCreds(String registryName, Long userId, Long workspaceId, String towerToken, String towerEndpoint) {
        if (!userId)
            throw new IllegalArgumentException("Missing userId parameter")
        if (!towerToken)
            throw new IllegalArgumentException("Missing Tower access token")

        final pairing = keyService.getPairingRecord(PairingService.TOWER_SERVICE, towerEndpoint)
        if (!pairing)
            throw new IllegalStateException("No exchange key registered for service ${PairingService.TOWER_SERVICE} at endpoint: ${towerEndpoint}")
        if (pairing.isExpired())
            log.debug("Exchange key registered for service ${PairingService.TOWER_SERVICE} at endpoint: ${towerEndpoint} used after expiration, should be renewed soon")

        final all = towerClient.listCredentials(towerEndpoint, towerToken, workspaceId).get().credentials

        if (!all) {
            log.debug "No credentials found for userId=$userId; workspaceId=$workspaceId; endpoint=$towerEndpoint"
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
        final creds = findBestMatchingCreds(matchingRegistryName, all)
        if (!creds) {
            log.debug "No credentials matching criteria registryName=$registryName; userId=$userId; workspaceId=$workspaceId; endpoint=$towerEndpoint"
            return null
        }

        // log for debugging purposes
        log.debug "Credentials matching criteria registryName=$registryName; userId=$userId; workspaceId=$workspaceId; endpoint=$towerEndpoint => $creds"
        // now fetch the encrypted key
        final encryptedCredentials = towerClient.fetchEncryptedCredentials(towerEndpoint, towerToken, creds.id, pairing.pairingId, workspaceId).get()
        final privateKey = pairing.privateKey
        final credentials = decryptCredentials(privateKey, encryptedCredentials.keys)
        return parsePayload(credentials)
    }

    //Find best match for a registry name
    CredentialsDescription findBestMatchingCreds(String containerRepository, List<CredentialsDescription> credsList) {
        int bestMatchIndex = -1
        int longestPartialMatch = 0

        for(int i =0; i<credsList.size(); i++){
            def cred = credsList[i]
            String registry = cred.registry

            //if its an exact match
            if(containerRepository.equalsIgnoreCase(registry)) {
                return cred
            }

            //to check for partial match
            if (containerRepository.startsWith(registry)) {
                int partialMatchLength = registry.length()

                if (partialMatchLength > longestPartialMatch) {
                    longestPartialMatch = partialMatchLength
                    bestMatchIndex = i
                }
            }
        }
        return bestMatchIndex != -1?credsList[bestMatchIndex]:null
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
