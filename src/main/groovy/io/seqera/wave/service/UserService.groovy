package io.seqera.wave.service

import io.seqera.wave.tower.User
import reactor.core.publisher.Mono

/**
 * Declare a service to access a Tower user
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface UserService {

    User getUserByAccessToken(String accessToken)

    Mono<User> getUserByAccessTokenAsync(String accessToken)

}
