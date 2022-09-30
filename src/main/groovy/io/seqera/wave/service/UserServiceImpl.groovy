package io.seqera.wave.service

import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject
import jakarta.inject.Singleton
import reactor.core.publisher.Mono

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
    User getUserByAccessToken(String accessToken) {
        getUserByAccessTokenAsync(accessToken).block()
    }

    @Override
    Mono<User> getUserByAccessTokenAsync(String encodedToken) {
        Mono.create { emitter ->
            if (!towerClient)
                emitter.error(new IllegalStateException("Missing Tower client - make sure the 'tower' micronaut environment has been provided"))

            towerClient.userInfo(encodedToken).subscribe({resp->
                if (!resp || !resp.user)
                    emitter.error(new UnauthorizedException("Unauthorized - Make sure you have provided a valid access token"))
                log.debug("Authorized user=$resp.user")
                emitter.success(resp.user)
            }, {t->
                emitter.error(t)
            })
        }
    }

}
