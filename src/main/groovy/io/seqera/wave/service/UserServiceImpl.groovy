package io.seqera.wave.service

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.TowerClientDelegate
import io.seqera.wave.tower.client.UserInfoResponse
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
    private TowerClientDelegate towerClient

    @Override
    CompletableFuture<User> getUserByAccessTokenAsync(String endpoint, String encodedToken) {
        if( !towerClient )
            throw new IllegalStateException("Missing Tower client - make sure the 'tower' micronaut environment has been provided")

        towerClient.userInfo(endpoint,encodedToken).handle( (UserInfoResponse resp, Throwable error) -> {
            if( error )
                throw error
            if (!resp || !resp.user)
                throw new UnauthorizedException("Unauthorized - Make sure you have provided a valid access token")
            log.debug("Authorized user=$resp.user")
            return resp.user
        })
    }

    @Override
    User getUserByAccessToken(String endpoint, String encodedToken) {
        try {
            return getUserByAccessTokenAsync(endpoint, encodedToken).get()
        }
        catch(ExecutionException e){
            throw e.cause
        }
    }
}
