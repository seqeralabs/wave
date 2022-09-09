package io.seqera.wave.service

import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.tower.Credentials
import io.seqera.wave.tower.CredentialsDao
import io.seqera.wave.tower.SecretDao
import io.seqera.tower.crypto.CryptoHelper
import io.seqera.tower.crypto.Sealed
import io.seqera.wave.util.StringUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton
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

    static final public String DOCKER_IO = 'docker.io'

    @Inject
    private CredentialsDao credentialsDao

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

    ContainerRegistryKeys findRegistryCreds(String registryName, Long userId, Long workspaceId) {
        if (!userId)
            throw new IllegalArgumentException("Missing userId parameter")
        
        final all = workspaceId
                ? credentialsDao.findRegistryCredentialsByWorkspaceId(workspaceId)
                : credentialsDao.findRegistryCredentialsByUser(userId)

        if (!all)
            return null

        // find a credentials with a matching registry
        final matching = new ArrayList<Credentials>(10)
        for (Credentials it : all) {
            final keys = parsePayload(it.keys)
            if (keys == null)
                continue

            final r1 = registryName ?: DOCKER_IO
            final r2 = keys.registry ?: DOCKER_IO
            if (r1 == r2) {
                matching.add(it)
                break
            }
        }

        if (!matching)
            return null

        Credentials creds = matching.first()
        if( matching.size()>1 && userId && workspaceId ) {
            // try to resolve ambiguity finding a creds that matches the userId
            creds = matching.find( it -> it.user.id == userId )
        }

        // now find the matching secret
        final secretKey = "/user/${creds.user.id}/creds/${creds.id}"
        final hashedKey = CryptoHelper.encodeSecret(secretKey, creds.salt).getDataString()
        final secret = secretDao.findById(hashedKey).orElse(null)
        if (!secret) {
            log.debug "Unable to find secret for credentials id: $creds.id"
            return null
        }
        final sealedObj = Sealed.deserialize(secret.secure)
        final json = new String(crypto.decrypt(sealedObj, creds.salt))

        return parsePayload(json)
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
