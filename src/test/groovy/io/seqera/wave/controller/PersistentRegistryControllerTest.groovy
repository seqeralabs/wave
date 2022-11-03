package io.seqera.wave.controller

import spock.lang.Specification
import spock.lang.Timeout

import io.micronaut.context.ApplicationContext
import io.micronaut.core.io.socket.SocketUtils
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exchange.RegistryErrorResponse
import io.seqera.wave.model.ContentType
import io.seqera.wave.storage.RedisStorage
import io.seqera.wave.test.DockerRegistryContainer
import io.seqera.wave.test.RedisTestContainer
import io.seqera.wave.test.SurrealDBTestContainer
import redis.clients.jedis.Jedis
/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
@MicronautTest
class PersistentRegistryControllerTest extends Specification implements DockerRegistryContainer, SurrealDBTestContainer{

    EmbeddedServer embeddedServer

    int port

    def setup() {
        restartDb()
        port = SocketUtils.findAvailableTcpPort()
        embeddedServer = ApplicationContext.run(EmbeddedServer,
                [surrealdb: [
                        user     : 'root',
                        password : 'root',
                        ns       : 'test',
                        db       : 'test',
                        url      : surrealDbURL,
                        'init-db': false]
                ]+[
                'wave.build.timeout':'3s',
                'micronaut.server.port': port,
                'micronaut.http.services.default.url' : "http://localhost:$port".toString(),
        ], 'test', 'h2', 'surrealdb')

        initRegistryContainer(applicationContext)
    }

    def cleanup(){
    }

    ApplicationContext getApplicationContext() {
        embeddedServer.applicationContext
    }

    void 'should count a get manifest'() {
        given:
        HttpClient client = applicationContext.createBean(HttpClient)
        HttpClient surrealClient = HttpClient.create(new URL(surrealDbURL))

        when:
        HttpRequest request = HttpRequest.HEAD("http://localhost:$port/v2/library/hello-world/manifests/latest").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        HttpResponse<String> response = client.toBlocking().exchange(request,String)
        sleep 1_000 //let persistent save the object
        then:
        response.status() == HttpStatus.OK
        response.headers.each {println "$it.key=$it.value"}

        and:
        response.getHeaders().get('docker-content-digest').startsWith( 'sha256:')
        response.getHeaders().get('Content-Type') == 'application/vnd.docker.distribution.manifest.v2+json'
        response.getContentLength() == 525

        and:
        def map = surrealClient.toBlocking()
                .retrieve(
                        HttpRequest.GET("/key/wave_stats")
                                .headers([
                                        'ns'          : 'test',
                                        'db'          : 'test',
                                        'User-Agent'  : 'micronaut/1.0',
                                        'Accept': 'application/json'])
                                .basicAuth('root', 'root'), Map<String, Object>)
        map.result.size()
        map.result.first().image == 'docker.io/library/hello-world'
    }
}
