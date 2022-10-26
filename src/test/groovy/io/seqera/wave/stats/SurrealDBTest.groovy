package io.seqera.wave.stats

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.stats.surrealdb.SurrealStorage
import io.seqera.wave.test.SurrealDBTestContainer
import io.seqera.wave.tower.User

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class SurrealDBTest extends Specification implements SurrealDBTestContainer {

    ApplicationContext applicationContext

    String getSurrealDbURL() {
        "http://$surrealHostName:$surrealPort"
    }

    def setup() {
        restartDb()
        applicationContext = ApplicationContext.run([
                        surrealdb: [
                                user     : 'root',
                                password : 'root',
                                ns       : 'test',
                                db       : 'test',
                                url      : surrealDbURL,
                                'init-db': false
                        ]]
        , 'test', 'surrealdb')
    }

    void "can connect"() {
        given:
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))

        when:
        def str = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.POST("/sql", "SELECT * FROM count()")
                                .headers(['ns': 'test', 'db': 'test', 'accept':'application/json'])
                                .basicAuth('root', 'root'), Map<String, String>)

        then:
        str.result.first() == 1
    }

    void "can insert an async build"() {
        given:
        final String dockerFile = """\
            FROM quay.io/nextflow/bash
            RUN echo "Look ma' building 🐳🐳 on the fly!" > /hello.txt
            ENV NOW=${System.currentTimeMillis()}
            """

        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))
        SurrealStorage storage = applicationContext.getBean(SurrealStorage)
        BuildRecord build = new BuildRecord(
                buildId: 'test',
                dockerFile: dockerFile,
                condaFile: 'test',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                ip: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0,
        )

        when:
        storage.initializeDb()

        storage.storeBuild(build)

        sleep 100 //as we are using async, let database a while to store the item
        then:
        def map = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.GET("/key/build_wave")
                                .headers([
                                        'ns'          : 'test',
                                        'db'          : 'test',
                                        'User-Agent'  : 'micronaut/1.0',
                                        'Accept': 'application/json'])
                                .basicAuth('root', 'root'), Map<String, Object>)
        map.result.size()
        map.result.first().ip == '127.0.0.1'
    }

    void "can't insert a build but ends without error"() {
        given:
        SurrealStorage storage = applicationContext.getBean(SurrealStorage)
        BuildRecord build = new BuildRecord(
                buildId: 'test',
                dockerFile: 'test',
                condaFile: 'test',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                ip: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0,
        )

        when:
        surrealContainer.stop()

        storage.storeBuild(build)

        sleep 100 //as we are using async, let database a while to store the item
        then:
        true
    }

    void "an event insert a build"() {
        given:
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))
        SurrealStorage storage = applicationContext.getBean(SurrealStorage)
        storage.initializeDb()
        StatsService service = applicationContext.getBean(StatsService)
        BuildRequest request = new BuildRequest("test", Path.of("."), "test", "test", Mock(User), ContainerPlatform.of('amd64'),'{auth}', "test", "127.0.0.1")
        BuildResult result = new BuildResult("id", 0, "content", Instant.now(), Duration.ofSeconds(1))
        BuildEvent event = new BuildEvent(buildRequest: request, buildResult: result)

        when:
        service.onApplicationEvent(event)
        sleep 100 //as we are using async, let database a while to store the item
        then:
        def map = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.GET("/key/build_wave")
                                .headers([
                                        'ns'          : 'test',
                                        'db'          : 'test',
                                        'User-Agent'  : 'micronaut/1.0',
                                        'Accept': 'application/json'])
                                .basicAuth('root', 'root'), Map<String, Object>)
        map.result.size()
        map.result.first().ip == '127.0.0.1'
    }

    void "an event is not inserted if no database"() {
        given:
        surrealContainer.stop()
        StatsService service = applicationContext.getBean(StatsService)
        BuildRequest request = new BuildRequest("test", Path.of("."), "test", "test", Mock(User), ContainerPlatform.of('amd64'),'{auth}', "test", "127.0.0.1")
        BuildResult result = new BuildResult("id", 0, "content", Instant.now(), Duration.ofSeconds(1))
        BuildEvent event = new BuildEvent(buildRequest: request, buildResult: result)

        when:
        service.onApplicationEvent(event)
        sleep 100 //as we are using async, let database a while to store the item
        then:
        true
    }
}
