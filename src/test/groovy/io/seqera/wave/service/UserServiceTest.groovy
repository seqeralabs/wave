/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

import spock.lang.Specification

import java.util.concurrent.CompletableFuture

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.TowerClient
import io.seqera.wave.tower.client.UserInfoResponse
import jakarta.inject.Inject

import static io.seqera.wave.util.FutureUtils.completeExceptionally

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class UserServiceTest extends Specification {

    @Inject
    TowerClient client

    @Inject
    UserService service

    @MockBean(TowerClient)
    TowerClient mockConnector() {
        Mock(TowerClient)
    }

    def 'should auth user' () {
        given:
        def endpoint = "https://foo.com/tower"
        def token = "a valid token"
        def auth = JwtAuth.of(endpoint, token)

        when: // a valid token
        def user = service.getUserByAccessToken(endpoint, auth)
        then:
        1 * client.userInfo(endpoint,auth) >> CompletableFuture.completedFuture(new UserInfoResponse(user:new User(id: 1)))
        and:
        user.id == 1

    }

    def 'should not auth user' () {
        given:
        def endpoint = "https://foo.com/tower"
        def token = "a invalid token"
        def auth = JwtAuth.of(endpoint, token)

        when: // an invalid token
        service.getUserByAccessToken(endpoint,auth)
        then:
        1 * client.userInfo(endpoint,auth) >> completeExceptionally(new HttpResponseException(401, "Auth error"))
        and:
        def exp = thrown(HttpResponseException)
        exp.statusCode().code == 401
    }

}
