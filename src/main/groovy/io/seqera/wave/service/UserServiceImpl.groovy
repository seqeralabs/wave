/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service

import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutionException
import io.micronaut.core.annotation.Nullable

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
