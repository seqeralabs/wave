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

import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Timeout

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.seqera.fixtures.redis.RedisTestContainer
import io.seqera.wave.exchange.RegistryErrorResponse
import io.seqera.wave.model.ContentType
import io.seqera.wave.service.builder.BuildEntry
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.impl.BuildStateStoreImpl
import io.seqera.wave.service.job.JobFactory
import io.seqera.wave.service.job.JobProcessingQueue
import io.seqera.wave.service.request.ContainerRequest
import io.seqera.wave.service.request.ContainerRequestStoreImpl
import io.seqera.wave.storage.ManifestCacheStore
import io.seqera.wave.test.DockerRegistryContainer
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
class RegistryControllerRedisTest extends Specification implements DockerRegistryContainer, RedisTestContainer {

    @Shared
    ApplicationContext applicationContext

    @Shared
    int port

    def setup() {
        EmbeddedServer server = ApplicationContext.run(EmbeddedServer, [
                'wave.build.timeout':'2s',
                'wave.build.trusted-timeout':'2s'
        ], 'test', 'redis')
        and:
        port = server.port
        applicationContext = server.getApplicationContext()
    }

    def cleanup(){
        applicationContext.close()
    }

    void 'should get manifest'() {
        given:
        HttpClient client = applicationContext.getBean(HttpClient)
        ManifestCacheStore storage = applicationContext.getBean(ManifestCacheStore)

        when:
        HttpRequest request = HttpRequest.GET("http://localhost:${port}/v2/library/hello-world/manifests/sha256:53641cd209a4fecfc68e21a99871ce8c6920b2e7502df0a20671c6fccc73a7c6").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        then:
        response.status() == HttpStatus.OK
        and:
        response.body().indexOf('"schemaVersion":') != -1
        response.getContentType().get().getName() ==  'application/vnd.oci.image.index.v1+json'
        response.header('docker-content-digest') == 'sha256:53641cd209a4fecfc68e21a99871ce8c6920b2e7502df0a20671c6fccc73a7c6'
        response.getContentLength() == 10242
    }

    @Timeout(30)
    void 'should return a timeout when build failed'() {
        given:
        def client = applicationContext.createBean(HttpClient)
        def buildCacheStore = applicationContext.getBean(BuildStateStoreImpl)
        def tokenCacheStore = applicationContext.getBean(ContainerRequestStoreImpl)
        def jobQueue = applicationContext.getBean(JobProcessingQueue)
        def jobFactory = applicationContext.getBean(JobFactory)
        def res = BuildResult.create('1')
        def req = new BuildRequest(
                targetImage: 'library/hello-world',
                containerId: '12345',
                buildId: 'bd-12345_1',
                startTime: Instant.now(),
                maxDuration: Duration.ofSeconds(5),
                workspace: Path.of('/some/work/dir')
        )
        def entry = new BuildEntry(req, res)
        def containerRequestData = ContainerRequest.of(identity: new PlatformId(new User(id:1)), containerImage: "library/hello-world")
        def job = jobFactory.build(req).withLaunchTime(Instant.now())
        and:
        tokenCacheStore.put("1234", containerRequestData)
        buildCacheStore.put("library/hello-world", entry)
        jobQueue.offer(job)

        when:
        HttpRequest request = HttpRequest.GET("http://localhost:${port}/v2/wt/1234/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        client.toBlocking().exchange(request,String)
        then:
        final exception = thrown(HttpClientResponseException)
        RegistryErrorResponse registryError = exception.response.getBody(RegistryErrorResponse).get()
        def error = registryError.errors.get(0)
        error.message.contains('Container image build timed out \'library/hello-world\'')
        error.code == 'UNKNOWN'
    }
}
