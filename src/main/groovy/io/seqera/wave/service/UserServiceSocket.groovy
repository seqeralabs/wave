package io.seqera.wave.service

import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import io.seqera.wave.service.pairing.socket.PairingChannel
import io.seqera.wave.service.pairing.socket.msg.UserRequest
import io.seqera.wave.service.pairing.socket.msg.UserResponse
import io.seqera.wave.tower.User
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Named('socket')
@CompileStatic
class UserServiceSocket implements UserService {

    @Inject
    private PairingChannel channel

    @Override
    User getUserByAccessToken(String endpoint, String accessToken) {
        getUserByAccessTokenAsync(endpoint, accessToken).get()
    }

    @Override
    CompletableFuture<User> getUserByAccessTokenAsync(String endpoint, String accessToken) {
        CompletableFuture<UserResponse> future = channel.send(endpoint, new UserRequest(accessToken))
        return future.thenApply( (UserResponse resp) -> resp.user ).orTimeout(5, TimeUnit.SECONDS)
    }
}
