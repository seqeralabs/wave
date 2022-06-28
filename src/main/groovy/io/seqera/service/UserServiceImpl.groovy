package io.seqera.service

import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.tower.AccessToken
import io.seqera.tower.AccessTokenDao
import io.seqera.tower.User
import io.seqera.tower.crypto.HmacSha1Signature
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Slf4j
@Singleton
class UserServiceImpl implements UserService {

    @Canonical
    static class DecodedAccessToken {
        Long tokenId
        String payload
        String signature

        static DecodedAccessToken fromString(String encodedToken) {
            final parts = new String(encodedToken.decodeBase64()).tokenize('.')
            final payload = parts[0]
            final signature = parts[1]

            final object = new JsonSlurper().parseText(payload) as Map
            final tokenId = object.get('tid') as Long

            return new DecodedAccessToken(tokenId, payload, signature)
        }
    }

    @Inject
    AccessTokenDao accessTokenDao

    @Override
    User getUserByAccessToken(String encodedToken) {

        final decoded = DecodedAccessToken.fromString(encodedToken)
        final token = accessTokenDao.findById(decoded.tokenId).orElse(null)
        if( !token) {
            log.warn "Unable to find any user for tokenId: '$decoded.tokenId'"
            return null
        }
        if( !isValid(token, decoded) ) {
            log.warn "Access Token is not valid: '$decoded.tokenId'"
            return null
        }

        return token.user
    }

    boolean isValid(AccessToken accessToken, DecodedAccessToken jwt) {
        return HmacSha1Signature.compute(jwt.payload, accessToken.secret) == jwt.signature
    }
}
