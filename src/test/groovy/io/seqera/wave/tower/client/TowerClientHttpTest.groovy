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

package io.seqera.wave.tower.client

import spock.lang.Specification

import java.util.concurrent.ExecutionException

import io.micronaut.cache.CacheManager
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.wave.tower.client.connector.TowerConnector
import jakarta.inject.Inject

@MicronautTest(environments = ['test', 'legacy-http-connector'])
@Property(name = 'spec.name', value = 'TowerClientHttpTest')
@Property(name = 'wave.pairing.channel.maxAttempts', value = '0')
class TowerClientHttpTest extends Specification{

    @Controller('/')
    @Requires(property = 'spec.name', value = 'TowerClientHttpTest')
    static class TowerFakeController {

        @Get('/user-info')
        HttpResponse<UserInfoResponse> userInfo(@Header('Authorization') String authorization) {
            if (authorization == 'Bearer foo') {
                return HttpResponse.unauthorized()
            }
            if (authorization == 'Bearer refresh') {
                return HttpResponse.unauthorized()
            }
            HttpResponse.ok(new UserInfoResponse(user: new User(id: 1)))
        }

        @Post('/oauth/access_token')
        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        HttpResponse<Void> oauthRefresh(String grant_type, String refresh_token) {
            if (grant_type == 'refresh_token' && refresh_token == 'refresh'){
                return HttpResponse.ok()
                        .cookie(Cookie.of("JWT","refreshed"))
                        .cookie(Cookie.of("JWT_REFRESH_TOKEN",'refresh'))
            } else {
                return HttpResponse.badRequest()
            }
        }

        @Get('/credentials')
        HttpResponse<ListCredentialsResponse> listCredentials(@Header('Authorization') String authorization, @Nullable @QueryValue('workspaceId') Long workspaceId) {
            if (authorization == 'Bearer foo') {
                return HttpResponse.unauthorized()
            }
            if (authorization == 'Bearer refresh') {
                return HttpResponse.unauthorized()
            }
            if (workspaceId == 1) {
                return HttpResponse.ok(new ListCredentialsResponse(credentials: List.of(
                        new CredentialsDescription(id: 'id0', provider: 'container_reg', registry: 'quay.io')
                )))
            } else {
                return HttpResponse.ok(new ListCredentialsResponse(credentials: List.of(
                        new CredentialsDescription(id: 'id1', provider: 'container_reg', registry: 'docker'),
                        new CredentialsDescription(id: 'id2', provider: 'aws', registry: null))))
            }
        }

        @Get('/credentials/{credentialsId}/keys')
        HttpResponse<GetCredentialsKeysResponse> getCredentialsKeys(@Header('Authorization')String authorization, String credentialsId, @QueryValue String keyId) {
            if (authorization == 'Bearer foo') {
                return HttpResponse.unauthorized()
            }
            if (authorization == 'Bearer refresh') {
                return HttpResponse.unauthorized()
            }
            if (credentialsId == '1' && keyId == '1') {
                return HttpResponse.ok(new GetCredentialsKeysResponse(keys: 'keys'))
            } else {
                throw new NotFoundException("Unable to find credentials with id: 1")
            }
        }

    }

    @Inject
    EmbeddedServer embeddedServer

    @Inject
    TowerClient towerClient

    @Inject
    JwtAuthStore jwtAuthStore

    @Inject
    CacheManager cacheManager

    @Inject
    TowerConnector towerConnector

    def setup() {
        jwtAuthStore.clear()
        cacheManager.getCache("cache-tower-client").invalidateAll()
        cacheManager.getCache("cache-registry-proxy").invalidateAll()
        towerConnector.refreshCache0().invalidateAll()
    }

    private String getHostName() {
        return embeddedServer.getURL().toString()
    }

    def 'handle connection failure'() {
        given:
        def endpoint = "https://10.255.255.1" // this is  a non routable address to simulate connection errors
        def auth = JwtAuth.of(endpoint, 'token')
        when: 'contacting tower'
        towerClient.userInfo(endpoint,auth).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause  as HttpResponseException).statusCode() == HttpStatus.SERVICE_UNAVAILABLE
    }

    def "test user-info"() {
        given:
        def auth = JwtAuth.of(hostName, 'token')
        when: 'requesting user info with a valid token'
        def resp = towerClient.userInfo(hostName,auth).get()
        then:
        resp.user.id == 1
    }

    def 'test user-info with refreshable token'() {
        given:
        def auth = JwtAuth.of(hostName, 'refresh', 'refresh')
        when:
        def resp = towerClient.userInfo(hostName, auth).get()
        then:
        resp.user.id == 1
    }

    def 'test user-info with invalid token'() {
        given:
        def auth = JwtAuth.of(hostName, 'foo')
        when:
        towerClient.userInfo(hostName,auth).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
    }

    def 'test user-info with token that cannot be refreshed'() {
        given:
        def auth = JwtAuth.of(hostName, 'refresh',  'unrefreshable')
        when:
        towerClient.userInfo(hostName,auth).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.BAD_REQUEST
    }

    def 'test list-credentials'() {
        given:
        def auth = JwtAuth.of(hostName, 'token')
        when: 'requesting credentials'
        def resp = towerClient.listCredentials(hostName, auth,null).get()
        then:
        resp.credentials.size() == 2
        resp.credentials[0].id == 'id1'
        resp.credentials[0].provider == 'container_reg'
        resp.credentials[0].registry == 'docker'
        resp.credentials[1].id == 'id2'
        resp.credentials[1].provider == 'aws'
        resp.credentials[1].registry == null

        when: 'requesting credentials in a workspace that exists'
        resp = towerClient.listCredentials(hostName, auth, 1).get()
        then:
        resp.credentials.size() == 1
        resp.credentials[0].id == 'id0'
        resp.credentials[0].provider == 'container_reg'
        resp.credentials[0].registry == 'quay.io'
    }

    def 'test list-credentials with refreshable token'() {
        given:
        def auth = JwtAuth.of(hostName, 'refresh', 'refresh')
        when:
        def resp = towerClient.listCredentials(hostName, auth,null).get()
        then:
        resp.credentials.size() == 2
        resp.credentials[0].id == 'id1'
        resp.credentials[0].provider == 'container_reg'
        resp.credentials[0].registry == 'docker'
        resp.credentials[1].id == 'id2'
        resp.credentials[1].provider == 'aws'
        resp.credentials[1].registry == null
    }

    def 'test list-credentials with invalid token'() {
        given:
        def auth = JwtAuth.of(hostName, 'foo')
        when:
        towerClient.listCredentials(hostName,auth, null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
    }

    def 'test list-credentials with token that cannot be refreshed'() {
        given:
        def auth = JwtAuth.of(hostName, 'refresh', 'unrefreshable')
        when:
        towerClient.listCredentials(hostName, auth, null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.BAD_REQUEST
    }

    def 'test fetch-credentials'() {
        given:
        def auth = JwtAuth.of(hostName, 'token')
        when:
        def resp = towerClient.fetchEncryptedCredentials(hostName, auth,'1','1',null).get()
        then:
        resp.keys == 'keys'
    }

    def 'test fetch-credentials with refreshable token'() {
        given:
        def auth = JwtAuth.of(hostName, 'refresh', 'refresh')
        when:
        def resp = towerClient.fetchEncryptedCredentials(hostName, auth, '1', '1', null).get()
        then:
        resp.keys == 'keys'
    }

    def 'test fetch-credentials with invalid token'() {
        given:
        def auth = JwtAuth.of(hostName, 'foo')
        when:
        towerClient.fetchEncryptedCredentials(hostName, auth, '1', '1',null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
    }

    def 'test fetch-credentials with token that cannot be refreshed'() {
        given:
        def auth = JwtAuth.of(hostName, 'refresh', 'unrefreshable')
        when:
        towerClient.fetchEncryptedCredentials(hostName, auth, '1', '1', null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.BAD_REQUEST
    }

    def 'parse tokens'() {
        given:
        def auth = JwtAuth.of('http://foo.com', '12345', 'current-refresh')
        when:
        def tokens = TowerConnector.parseTokens(COOKIES,auth)

        then:
        tokens.refresh == EXPECTED_REFRESH
        tokens.bearer == EXPECTED_AUTH

        where:
        COOKIES                                                                                     || EXPECTED_AUTH | EXPECTED_REFRESH
        [ cookie('JWT','jwt') ]                                                                     || 'jwt'         | 'current-refresh'
        [ cookie('JWT','jwt1'), cookie('JWT_REFRESH_TOKEN','jwt-refresh1') ]                        || 'jwt1'        | 'jwt-refresh1'
        [ cookie('JWT_REFRESH_TOKEN','jwt-refresh2'),cookie('JWT','jwt2') ]                         || 'jwt2'        | 'jwt-refresh2'
        [ cookie('OTHER','other'),cookie('JWT','jwt3') ]                                            || 'jwt3'        | 'current-refresh'
        [ cookie('JWT','jwt'),cookie('JWT_REFRESH_TOKEN','jwt-refresh'),cookie('OTHER','other') ]   || 'jwt'         | 'jwt-refresh'

    }

    def 'parse tokens when there is no jwt'() {
        given:
        def auth = JwtAuth.of('http://foo.com', '12345', 'current-refresh')
        when:
        TowerConnector.parseTokens(COOKIES, auth)
        then:
        def e = thrown(HttpResponseException)
        e.statusCode() == HttpStatus.PRECONDITION_FAILED

        where:
        COOKIES || _
        []                                              || _
        [cookie('JWT_REFRESH_TOKEN','jwt-refresh')]     || _
        [cookie('OTHER','other')]                       || _
    }

    private static String cookie(String name, String value) {
        return new HttpCookie(name, value).toString()
    }

}
