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

package io.seqera.wave.tower.client

import spock.lang.Specification

import java.util.concurrent.ExecutionException
import javax.annotation.Nullable

import io.micronaut.cache.CacheManager
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
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
import io.seqera.wave.tower.auth.JwtAuthStore
import io.seqera.wave.tower.client.connector.TowerConnector
import jakarta.inject.Inject

@MicronautTest(environments = ['test','tower'])
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
                return HttpResponse.ok().cookie(Cookie.of("JWT","refreshed"))
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
    CacheManager cacheManager;

    def setup() {
        cacheManager.getCache("cache-20sec").invalidateAll()
    }

    private String getHostName() {
        return embeddedServer.getURL().toString()
    }

    def 'handle connection failure'() {
        when: 'contacting tower'
        def endpoint = "https://10.255.255.1" // this is  a non routable address to simulate connection errors
        towerClient.userInfo(endpoint,'token').get()

        then:
        def e = thrown(ExecutionException)
        (e.cause  as HttpResponseException).statusCode() == HttpStatus.SERVICE_UNAVAILABLE
    }

    def "test user-info"() {
        when: 'requesting user info with a valid token'
        def resp = towerClient.userInfo(hostName,"token").get()
        then:
        resp.user.id == 1
    }

    def 'test user-info with refreshable token'() {
        when:
        jwtAuthStore.putJwtAuth(hostName,'refresh','refresh')
        def resp = towerClient.userInfo(hostName, 'refresh').get()
        then:
        resp.user.id == 1
    }

    def 'test user-info with invalid token'() {
        when:
        towerClient.userInfo(hostName,'foo').get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
    }

    def 'test user-info with token that cannot be refreshed'() {
        when:
        jwtAuthStore.putJwtAuth(hostName, 'unrefreshable', 'refresh')
        towerClient.userInfo(hostName,'refresh').get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.BAD_REQUEST
    }

    def 'test list-credentials'() {
        when: 'requesting credentials'
        def resp = towerClient.listCredentials(hostName, 'token',null).get()
        then:
        resp.credentials.size() == 2
        resp.credentials[0].id == 'id1'
        resp.credentials[0].provider == 'container_reg'
        resp.credentials[0].registry == 'docker'
        resp.credentials[1].id == 'id2'
        resp.credentials[1].provider == 'aws'
        resp.credentials[1].registry == null

        when: 'requesting credentials in a workspace that exists'
        resp = towerClient.listCredentials(hostName, 'token', 1).get()
        then:
        resp.credentials.size() == 1
        resp.credentials[0].id == 'id0'
        resp.credentials[0].provider == 'container_reg'
        resp.credentials[0].registry == 'quay.io'
    }

    def 'test list-credentials with refreshable token'() {
        when:
        jwtAuthStore.putJwtAuth(hostName,'refresh','refresh')
        def resp = towerClient.listCredentials(hostName, 'refresh',null).get()
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
        when:
        towerClient.listCredentials(hostName,'foo', null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
    }

    def 'test list-credentials with token that cannot be refreshed'() {
        when:
        jwtAuthStore.putJwtAuth(hostName, 'unrefreshable', 'refresh')
        towerClient.listCredentials(hostName,'refresh', null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.BAD_REQUEST
    }

    def 'test fetch-credentials'() {
        when:
        def resp = towerClient.fetchEncryptedCredentials(hostName, 'token','1','1',null).get()
        then:
        resp.keys == 'keys'
    }

    def 'test fetch-credentials with refreshable token'() {
        when:
        jwtAuthStore.putJwtAuth(hostName,'refresh','refresh')
        def resp = towerClient.fetchEncryptedCredentials(hostName, 'refresh', '1', '1', null).get()
        then:
        resp.keys == 'keys'
    }

    def 'test fetch-credentials with invalid token'() {
        when:
        towerClient.fetchEncryptedCredentials(hostName,'foo', '1', '1',null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
    }

    def 'test fetch-credentials with token that cannot be refreshed'() {
        when:
        jwtAuthStore.putJwtAuth(hostName, 'unrefreshable', 'refresh')
        towerClient.fetchEncryptedCredentials(hostName,'refresh', '1', '1', null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.BAD_REQUEST
    }

    def 'parse tokens'() {
        when:
        def tokens = TowerConnector.parseTokens(cookies,'current-refresh')

        then:
        tokens.refresh == expectedRefresh
        tokens.bearer == expectedAuth

        where:
        cookies                                                                                           || expectedAuth | expectedRefresh
        List.of(cookie('JWT','jwt'))                                                                      || 'jwt'         | 'current-refresh'
        List.of(cookie('JWT','jwt1'), cookie('JWT_REFRESH_TOKEN','jwt-refresh1'))                         || 'jwt1'        | 'jwt-refresh1'
        List.of(cookie('JWT_REFRESH_TOKEN','jwt-refresh2'),cookie('JWT','jwt2'))                          || 'jwt2'        | 'jwt-refresh2'
        List.of(cookie('OTHER','other'),cookie('JWT','jwt3'))                                             || 'jwt3'        | 'current-refresh'
        List.of(cookie('JWT','jwt'),cookie('JWT_REFRESH_TOKEN','jwt-refresh'),cookie('OTHER','other'))    || 'jwt'        | 'jwt-refresh'

    }

    def 'parse tokens when there is no jwt'() {
        when:
        TowerConnector.parseTokens(cookies,'current-refresh')
        then:
        def e = thrown(HttpResponseException)
        e.statusCode() == HttpStatus.PRECONDITION_FAILED

        where:
        cookies                                             || _
        List.of()                                           || _
        List.of(cookie('JWT_REFRESH_TOKEN','jwt-refresh'))  || _
        List.of(cookie('OTHER','other'))                    || _
    }

    private static String cookie(String name, String value) {
        return new HttpCookie(name, value).toString()
    }



}
