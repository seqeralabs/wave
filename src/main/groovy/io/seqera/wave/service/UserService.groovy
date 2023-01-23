package io.seqera.wave.service

import java.util.concurrent.CompletableFuture

import io.seqera.wave.tower.User

/**
 * Declare a service to access a Tower user
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface UserService {

    User getUserByAccessToken(String endpoint, String accessToken)

    CompletableFuture<User> getUserByAccessTokenAsync(String endpoint, String accessToken)

}
