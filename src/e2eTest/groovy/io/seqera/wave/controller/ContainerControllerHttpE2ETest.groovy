/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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
import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.objectstorage.InputStreamMapper
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.aws.AwsS3Configuration
import io.micronaut.objectstorage.aws.AwsS3Operations
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.core.RouteHandler
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.service.pairing.PairingServiceImpl
import io.seqera.wave.test.AwsS3TestContainer
import io.seqera.wave.tower.client.TowerClient
import jakarta.inject.Inject
import jakarta.inject.Named
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Property(name = 'wave.build.logs.bucket', value = 'test-bucket')
class ContainerControllerHttpE2ETest extends Specification implements AwsS3TestContainer {

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

    def 'should build conda image then store conda lockfile and fetch conda lockfile' () {
        given:
        def request = new SubmitContainerTokenRequest(
                packages: new PackagesSpec(channels: ['conda-forge'], entries: ['xz'], type: 'CONDA'),
                buildRepository: "test/repository",
                cacheRepository: "test/cache"

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
        long timeout = 120000
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
