package io.seqera.wave.service

import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.TowerClient
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

    @Inject
    @Nullable
    private TowerClient towerClient

    @Override
    User getUserByAccessToken(String encodedToken) {
        if( !towerClient )
            throw new IllegalStateException("Missing Tower client - make sure the 'tower' micronaut environment has been provided")
        try {
            final resp = towerClient.userInfo("Bearer $encodedToken")
            if( !resp || !resp.user )
                throw new UnauthorizedException("Unauthorized resp - Make sure you have provided a valid access token")
            log.debug("Authorized user=$resp.user")
            return resp.user
        }
        catch (Exception e) {
            throw new UnauthorizedException("Failed access - Make sure you have provided a valid access token", e)
        }
    }


}
