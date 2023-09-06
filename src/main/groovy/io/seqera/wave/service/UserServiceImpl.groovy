/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.service

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.exception.UnauthorizedException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.TowerClient
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
    private TowerClient towerClient

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
