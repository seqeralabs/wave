package io.seqera.wave.service

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.tower.crypto.EncryptedPacket
import io.seqera.wave.service.security.SecurityService
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
    TowerClient towerClient

    @Inject
    SecurityService keyService

    @Override
    ContainerRegistryKeys findRegistryCreds(String registryName, Long userId, Long workspaceId,String towerToken, String towerEndpoint) {

        if (!userId)
            throw new IllegalArgumentException("Missing userId parameter")
        if (!towerToken)
            throw new IllegalArgumentException("Missing tower token")

        final keyRecord = keyService.getServiceRegistration(SecurityService.TOWER_SERVICE, towerEndpoint)

        if (!keyRecord)
            throw new IllegalStateException("No exchange key registered for service ${SecurityService.TOWER_SERVICE} at endpoint: ${towerEndpoint}")

        final towerHostName = keyRecord.hostname

        final all = towerClient.listCredentials(towerHostName,towerToken,workspaceId).get().credentials

        if (!all)
            return null

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
        final creds = all.find {
            it.provider == 'container-reg'  && (it.registry ?: DOCKER_IO) == matchingRegistryName
        }
        if (!creds)
            return null

        // now fetch the encrypted key
        final encryptedCredentials = towerClient.fetchEncryptedCredentials(towerHostName,towerToken,creds.id,keyRecord.keyId,workspaceId).get()
        final privateKey = keyRecord.privateKey
        final credentials = decryptCredentials(privateKey, encryptedCredentials.keys)

        return parsePayload(credentials)
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
