package io.seqera.wave.service

import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.tower.crypto.HmacSha1Signature
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.tower.AccessToken
import io.seqera.wave.tower.AccessTokenDao
import io.seqera.wave.tower.User
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Define a service to access a Tower user
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
    private AccessTokenDao accessTokenDao

    @Override
    User getUserByAccessToken(String encodedToken) {

        final decoded = DecodedAccessToken.fromString(encodedToken)
        final token = accessTokenDao.findById(decoded.tokenId).orElse(null)
        if( !token) {
            log.warn "Unable to find any user for tokenId: '$decoded.tokenId' - access token: $encodedToken"
            throw new UnauthorizedException("Unknown access token")
        }
        if( !isValid(token, decoded) ) {
            log.warn "Invalid access token: '$decoded.tokenId'- access token: $encodedToken"
            throw new UnauthorizedException("Invalid access token")
        }

        return token.user
    }

    boolean isValid(AccessToken accessToken, DecodedAccessToken jwt) {
        return HmacSha1Signature.compute(jwt.payload, accessToken.secret) == jwt.signature
    }
}
