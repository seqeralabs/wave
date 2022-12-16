package io.seqera.wave.service

import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.tower.crypto.AsymmetricCipher
import io.seqera.tower.crypto.EncryptedPacket
import io.seqera.wave.service.security.SecurityService
import io.seqera.wave.tower.CredentialsDao
import io.seqera.wave.tower.SecretDao
import io.seqera.tower.crypto.CryptoHelper
import io.seqera.wave.tower.client.CredentialsDescription
import io.seqera.wave.tower.client.TowerClient
import io.seqera.wave.util.StringUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton

import static io.seqera.wave.WaveDefault.DOCKER_IO

/**
 * Define operations to access container registry credentials from Tower
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(env = 'tower')
@CompileStatic
@Singleton
class CredentialServiceImpl implements CredentialsService {

    @Inject
    private CredentialsDao credentialsDao

    @Inject
    TowerClient towerClient

    @Inject
    SecurityService keyService

    @Inject
    private SecretDao secretDao

    private CryptoHelper crypto

    @Value('${tower.crypto.secretKey}')
    private String secretKey

    @PostConstruct
    private void init() {
        log.debug "Tower crypto key: ${StringUtils.redact(secretKey)}"
        crypto = new CryptoHelper(secretKey)
    }

    @Override
    ContainerRegistryKeys findRegistryCreds(String registryName, Long userId, Long workspaceId,String towerToken, String towerEndpoint) {

        if (!userId)
            throw new IllegalArgumentException("Missing userId parameter")
        if (!towerToken)
            throw new IllegalArgumentException("Missing tower token")

        final keyRecord = keyService.getServiceRegistration(SecurityService.TOWER_SERVICE, towerEndpoint)
        final towerHostName = keyRecord.hostname




        final all = towerClient.listCredentials(towerHostName,towerToken,workspaceId).get().credentials

        if (!all)
            return null

        // find a credentials with a matching registry
        final matching = new ArrayList<CredentialsDescription>(10)
        for (CredentialsDescription it : all) {
            if (it.provider != 'container-reg') {
                continue
            }

            final r1 = registryName ?: DOCKER_IO
            final r2 = it.registry ?: DOCKER_IO
            if (r1 == r2) {
                matching.add(it)
                break
            }
        }

        if (!matching)
            return null

        CredentialsDescription creds = matching.first()
        if (matching.size() > 1 && userId && workspaceId) {
            //TODO disambiguate? why should this be the case??
        }



        // now fetch the encrypted key
        final encryptedCredentials = towerClient.fetchEncryptedCredentials(towerHostName,towerToken,creds.id,keyRecord.keyId).get()
        final privateKey = keyRecord.privateKey


        final credentials = decryptCredentials(privateKey, encryptedCredentials.credentials)

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
