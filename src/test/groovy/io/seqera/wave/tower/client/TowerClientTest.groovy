package io.seqera.wave.tower.client

import javax.annotation.Nullable

import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.cookie.Cookie
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.tower.User
import jakarta.inject.Inject
import spock.lang.Specification

import java.util.concurrent.ExecutionException

@MicronautTest(environments = ['test','tower'])
@Property(name = 'spec.name', value = 'TowerClientTest')
class TowerClientTest extends Specification{

    @Controller('/')
    @Requires(property = 'spec.name', value = 'TowerClientTest')
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
    TowerAuthTokensService tokensService


    private String getHostName() {
        return embeddedServer.getURL().toString()
    }

    def "test user-info"() {
        when: 'requesting user info with a valid token'
        def resp = towerClient.userInfo(hostName,"token").get()
        then:
        resp.user.id == 1
    }

    def 'test user-info with refreshable token'() {
        when:
        tokensService.updateAuthTokens(hostName,'refresh','refresh')
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
        tokensService.updateAuthTokens(hostName, 'unrefreshable', 'refresh')
        towerClient.userInfo(hostName,'refresh').get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
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
        tokensService.updateAuthTokens(hostName,'refresh','refresh')
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
        tokensService.updateAuthTokens(hostName, 'unrefreshable', 'refresh')
        towerClient.listCredentials(hostName,'refresh', null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
    }

    def 'test fetch-credentials'() {
        when:
        def resp = towerClient.fetchEncryptedCredentials(hostName, 'token','1','1',null).get()
        then:
        resp.keys == 'keys'
    }

    def 'test fetch-credentials with refreshable token'() {
        when:
        tokensService.updateAuthTokens(hostName,'refresh','refresh')
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
        tokensService.updateAuthTokens(hostName, 'unrefreshable', 'refresh')
        towerClient.fetchEncryptedCredentials(hostName,'refresh', '1', '1', null).get()
        then:
        def e = thrown(ExecutionException)
        (e.cause as HttpResponseException).statusCode() == HttpStatus.UNAUTHORIZED
    }

    def 'parse tokens'() {
        when:
        def tokens = TowerClient.parseTokens(cookies,'current-refresh')

        then:
        tokens.refreshToken == expectedRefresh
        tokens.authToken == expectedAuth

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
        TowerClient.parseTokens(cookies,'current-refresh')
        then:
        def e = thrown(HttpResponseException)
        e.statusCode() == HttpStatus.UNAUTHORIZED

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
