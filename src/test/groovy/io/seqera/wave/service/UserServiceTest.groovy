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


import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.runtime.server.EmbeddedServer
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.UserInfoResponse

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class UserServiceTest extends Specification {

    @Requires(property = 'spec.name', value = 'UserServiceTest')
    @Controller("/")
    static class TowerController {

        @Get('/user-info')
        HttpResponse<UserInfoResponse> userInfo(@Header("Authorization") String authorization) {
            if( authorization == 'Bearer foo')
                return HttpResponse.unauthorized()
            HttpResponse.ok(new UserInfoResponse(user: new User(id:1)))
        }

    }

    def 'should auth user' () {
        given:
        int port = SocketUtils.findAvailableTcpPort()
        final host = "http://localhost:${port}"
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'spec.name': 'UserServiceTest',
                'micronaut.server.port':port,
                'tower.api.endpoint':host
        ], 'test','tower','h2')
        ApplicationContext ctx = server.applicationContext

        and:
        def service = ctx.getBean(UserService)

        when: // a valid token
        def token = "a valid token"
        def user = service.getUserByAccessToken(host,token)
        then:
        user.id == 1

        when: // an invalid token
        token = "foo"
        service.getUserByAccessToken(host,token)
        then:
        def exp = thrown(HttpResponseException)
        exp.statusCode().code == 401
    }

}
