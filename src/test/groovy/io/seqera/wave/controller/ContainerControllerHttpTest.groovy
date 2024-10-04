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

package io.seqera.wave.controller

import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.aws.AwsS3Configuration
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.core.RouteHandler
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.exchange.DescribeWaveContainerResponse
import io.seqera.wave.service.ContainerRequestData
import io.seqera.wave.service.pairing.PairingRecord
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.PairingServiceImpl
import io.seqera.wave.test.AwsS3TestContainer
import io.seqera.wave.tower.User
import io.seqera.wave.tower.auth.JwtAuth
import io.seqera.wave.tower.client.TowerClient
import io.seqera.wave.tower.client.UserInfoResponse
import jakarta.inject.Inject
import jakarta.inject.Named
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import static io.seqera.wave.service.pairing.PairingService.TOWER_SERVICE
import static io.seqera.wave.util.FutureUtils.completeExceptionally
/**
 * @author : jorge <jorge.aguilera@seqera.io>
 */
@MicronautTest
@Property(name = 'wave.build.logs.bucket', value = 'test-bucket')
class ContainerControllerHttpTest extends Specification implements AwsS3TestContainer {

    @Inject
    @Client("/")
    HttpClient httpClient

    @Inject
    PairingService pairingService

    @Inject
    TowerClient towerClient

    @Inject
    RouteHandler routeHandler

    @MockBean(PairingServiceImpl)
    PairingService mockPairingService(){
        Mock(PairingService)
    }

    @MockBean(TowerClient)
    TowerClient mockTowerClient() {
        Mock(TowerClient)
    }

    def s3Client = S3Client.builder()
            .endpointOverride(URI.create("http://${awsS3HostName}:${awsS3Port}"))
            .region(Region.EU_WEST_1)
            .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("accesskey", "secretkey")))
            .forcePathStyle(true)
            .build()

    @MockBean(ObjectStorageOperations)
    @Named('build-logs')
    ObjectStorageOperations mockObjectStorageOperations() {
        AwsS3Configuration configuration = new AwsS3Configuration('build-logs')
        configuration.setBucket("test-bucket")
        return new AwsS3Operations(configuration, s3Client, Mock(InputStreamMapper))
    }

    def 'should create token request for anonymous user' () {
        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64',)
        def resp = httpClient.toBlocking().exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)
        then:
        resp.status() == HttpStatus.OK
    }

    def 'should create token request for user 1' () {
        given:
        def endpoint = 'http://tower.nf'
        def token = '12345'
        def refresh = '2'
        def auth = JwtAuth.of(endpoint, token, refresh)
        and:
        pairingService.getPairingRecord(TOWER_SERVICE, endpoint) >> { new PairingRecord('tower', endpoint) }
        towerClient.userInfo(endpoint,auth) >> CompletableFuture.completedFuture(new UserInfoResponse(user:new User(id:1)))

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        towerAccessToken: token,
                        towerRefreshToken: refresh,
                        towerEndpoint: endpoint,
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64',)
        def resp = httpClient.toBlocking().exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)
        then:
        resp.status() == HttpStatus.OK
    }

    def 'should fails build request for user foo' () {
        given:
        def endpoint = 'http://tower.nf'
        def token = 'foo'
        def refresh = 'foo2'
        def auth = JwtAuth.of(endpoint, token, refresh)
        and:
        pairingService.getPairingRecord(TOWER_SERVICE, endpoint) >> { new PairingRecord('tower', endpoint) }
        towerClient.userInfo(endpoint, auth) >> completeExceptionally(new HttpResponseException(401, "Auth error"))

        when:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        towerAccessToken: token,
                        towerRefreshToken: refresh,
                        towerEndpoint: endpoint,
                        towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64',)
        and:
        httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)
                .body()

        then:
        def err = thrown(HttpClientResponseException)
        and:
        err.status == HttpStatus.UNAUTHORIZED
    }

    def 'should fail build request if the instance has not registered for key exchange' () {
        given:
        pairingService.getPairingRecord(TOWER_SERVICE,_) >> null

        and:
        def cfg = new ContainerConfig(workingDir: '/foo')
        def request = new SubmitContainerTokenRequest(
                towerAccessToken: 'x',
                towerEndpoint: "http://tower.nf",
                towerWorkspaceId: 10,
                containerImage: 'ubuntu:latest',
                containerConfig: cfg,
                containerPlatform: 'arm64'
        )

        when:
        httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/container-token",request), SubmitContainerTokenResponse).body()
        then:
        def err = thrown(HttpClientResponseException)
        and:
        err.status == HttpStatus.BAD_REQUEST
    }

    def 'should create build request' () {
        given:
        def cfg = new ContainerConfig(workingDir: '/foo')
        def req = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')

        when:
        def resp = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/container-token", req), SubmitContainerTokenResponse)
        then:
        resp.status() == HttpStatus.OK
        resp.body().containerToken
    }

    def 'should not retrieve an expired build request' () {
        given:
        def cfg = new ContainerConfig(workingDir: '/foo')
        def req = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')

        when:
        def resp = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/container-token", req), SubmitContainerTokenResponse)
        then:
        resp.status() == HttpStatus.OK
        resp.body().containerToken
    }

    def 'should retrieve a valid build request' () {
        given:
        def cfg = new ContainerConfig(workingDir: '/foo')
        def req = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')

        when:
        def resp = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("container-token", req), SubmitContainerTokenResponse)
        then:
        resp.status() == HttpStatus.OK
        resp.body().containerToken

        when:
        routeHandler.parse("/v2/wt/${resp.body().containerToken}/library/ubuntu/blobs/latest")
        then:
        noExceptionThrown()
    }

    def 'should validate same image' () {
        given:
        def cfg = new ContainerConfig(workingDir: '/foo')
        def request = new SubmitContainerTokenRequest(towerWorkspaceId: 10, containerImage: 'ubuntu:latest', containerConfig: cfg, containerPlatform: 'arm64')

        when:
        def resp = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)
        then:
        resp.status() == HttpStatus.OK
        resp.body().containerToken

        when:
        routeHandler.parse("/v2/wt/${resp.body().containerToken}/library/hello/blobs/latest")
        then:
        thrown(IllegalArgumentException)
    }

    def 'should create token request for anonymous user with include' () {
        given:
        def request = new SubmitContainerTokenRequest(
                containerImage: 'ubuntu:sha256:aa772c98400ef833586d1d517d3e8de670f7e712bf581ce6053165081773259d',
                containerIncludes: ['busybox:sha256:4be429a5fbb2e71ae7958bfa558bc637cf3a61baf40a708cb8fff532b39e52d0'],
                containerConfig: new ContainerConfig(workingDir: '/foo'),
                containerPlatform: 'arm64',)
        when:
        def resp = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)
        then:
        resp.status() == HttpStatus.OK
        and:
        def token = resp.body().containerToken
        token != null  

        when:
        def req2 = HttpRequest.GET("/container-token/$token")
        def resp2 = httpClient.toBlocking().exchange(req2, DescribeWaveContainerResponse)
        then:
        resp2.status() == HttpStatus.OK
        then:
        def layers = resp2.body().request.containerConfig.layers
        layers.size()==1
        layers[0].location == 'docker://docker.io/v2/library/busybox/blobs/sha256:7b2699543f22d5b8dc8d66a5873eb246767bca37232dee1e7a3b8c9956bceb0c'
        layers[0].gzipDigest == 'sha256:7b2699543f22d5b8dc8d66a5873eb246767bca37232dee1e7a3b8c9956bceb0c'
        layers[0].gzipSize == 2152262
        layers[0].tarDigest == 'sha256:95c4a60383f7b6eb6f7b8e153a07cd6e896de0476763bef39d0f6cf3400624bd'
    }

    def 'should get http status code 401 from delete token api when no credentials provided'() {
        when:
        def req = HttpRequest.DELETE("/container-token/token")
        def res = httpClient.toBlocking().exchange(req, Map)
        then:
        def e = thrown(HttpClientResponseException)
        e.status.code == 401
    }

    def 'should delete the record for the provided token from cache' () {
        given:
        def endpoint = 'http://tower.nf'
        def token = '12345'
        def refresh = "2"
        and:
        def auth = JwtAuth.of(endpoint, token, refresh)
        and:
        pairingService.getPairingRecord(TOWER_SERVICE, endpoint) >> { new PairingRecord('tower', endpoint) }
        towerClient.userInfo(endpoint, auth) >> CompletableFuture.completedFuture(new UserInfoResponse(user:new User(id:1)))

        and:
        def cfg = new ContainerConfig(workingDir: '/foo')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        towerAccessToken: token,
                        towerRefreshToken: refresh,
                        towerEndpoint: endpoint,
                        towerWorkspaceId: 10,
                        containerImage: 'ubuntu:latest',
                        containerConfig: cfg,
                        containerPlatform: 'arm64',)
        def resp = httpClient.toBlocking().exchange(HttpRequest.POST("/container-token", request), SubmitContainerTokenResponse)
        and:
        def waveToken = resp.body().containerToken

        when:
        def req = HttpRequest.DELETE("/container-token/$waveToken").basicAuth("username", "password")
        def deleteResp = httpClient.toBlocking().exchange(req, ContainerRequestData)

        then:
        deleteResp.status.code == 200
    }

    def 'should get the correct image name with imageSuffix name strategy'(){
        when:
        def packages = new PackagesSpec(channels: ['conda-forge', 'bioconda'], entries: ['salmon'], type: 'CONDA')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        nameStrategy: "imageSuffix",
                        packages: packages,
                        freeze: true,
                        buildRepository: "docker.io/foo/test")
        and:
        def response = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/v1alpha2/container", request), SubmitContainerTokenResponse)
                .body()

        then:
        response.targetImage.startsWith("docker.io/foo/test/salmon")
    }

    def 'should get the correct image name with tagPrefix name strategy'(){
        when:
        def packages = new PackagesSpec(channels: ['conda-forge', 'bioconda'], entries: ['salmon'], type: 'CONDA')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        nameStrategy: "tagPrefix",
                        packages: packages,
                        freeze: true,
                        buildRepository: "docker.io/foo/test")
        and:
        def response = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/v1alpha2/container", request), SubmitContainerTokenResponse)
                .body()

        then:
        response.targetImage.startsWith("docker.io/foo/test:salmon")
    }

    def 'should get the correct image name with default name strategy'(){
        when:
        def packages = new PackagesSpec(channels: ['conda-forge', 'bioconda'], entries: ['salmon'], type: 'CONDA')
        SubmitContainerTokenRequest request =
                new SubmitContainerTokenRequest(
                        packages: packages,
                        freeze: true,
                        buildRepository: "docker.io/foo/test")
        and:
        def response = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/v1alpha2/container", request), SubmitContainerTokenResponse)
                .body()

        then:
        response.targetImage.startsWith("docker.io/foo/test:salmon")
    }

    def 'should build conda image then store conda lockfile and fetch conda lockfile' () {
        given:
        def request = new SubmitContainerTokenRequest(
                packages: new PackagesSpec(channels: ['conda-forge'], entries: ['xz'], type: 'CONDA'),
                containerPlatform: 'arm64',
                buildRepository: "test/repository",
                cacheRepository: "test/cache",

        )
        and:
        s3Client.createBucket { it.bucket("test-bucket") }

        when:
        def res = httpClient
                .toBlocking()
                .exchange(HttpRequest.POST("/v1alpha2/container",request), SubmitContainerTokenResponse).body()
        and:
        awaitBuild(res.buildId)
        and:
        res = httpClient
                .toBlocking()
                .exchange(HttpRequest.GET("/v1alpha1/builds/$res.buildId/condalock"), String).body()

        then:
        res.contains('conda create --name <env> --file <this file>')
    }

    boolean awaitBuild(String buildId) {
        long startTime = System.currentTimeMillis()
        long timeout = 60000
        long checkInterval = 5000
        while (System.currentTimeMillis() - startTime < timeout) {
            def res = httpClient
                    .toBlocking()
                    .exchange(HttpRequest.GET("/v1alpha1/builds/$buildId/status"), BuildStatusResponse)
                    .body()

            if (res.status == BuildStatusResponse.Status.COMPLETED) {
                return true
            }
            sleep checkInterval
        }

        return false
    }
}
