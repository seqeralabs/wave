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
