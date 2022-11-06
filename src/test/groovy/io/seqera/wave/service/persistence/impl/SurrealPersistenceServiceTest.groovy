package io.seqera.wave.service.persistence.impl

import spock.lang.Ignore
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
import io.seqera.wave.service.persistence.BuildRecord
import io.seqera.wave.service.persistence.CondaRecord
import io.seqera.wave.test.SurrealDBTestContainer
/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
class SurrealPersistenceServiceTest extends Specification implements SurrealDBTestContainer {

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

    def cleanup() {
        applicationContext.close()
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
        final String condaFile = """
            echo "Look ma' building 🐳🐳 on the fly!" > /hello.txt
        """
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))
        SurrealPersistenceService storage = applicationContext.getBean(SurrealPersistenceService)
        BuildRequest request = BuildRequest
                .builder()
                .withId('100')
                .withDockerFile(dockerFile)
                .withWorkDir(Path.of('/some/dir'))
                .withCondaFile(condaFile)
                .withPlatform(ContainerPlatform.of('amd64'))
                .withRequestIp("127.0.0.1")
                .build()
        BuildResult result = new BuildResult(request.id, -1, "ok", Instant.now(), Duration.ofSeconds(3))
        BuildEvent event = new BuildEvent(request, result)
        BuildRecord build = BuildRecord.fromEvent(event)

        when:
        storage.initializeDb()

        storage.saveBuild(build)

        sleep 100 //as we are using async, let database a while to store the item
        then:
        def map = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.GET("/key/wave_build")
                                .headers([
                                        'ns'          : 'test',
                                        'db'          : 'test',
                                        'User-Agent'  : 'micronaut/1.0',
                                        'Accept': 'application/json'])
                                .basicAuth('root', 'root'), Map<String, Object>)
        map.result.size()
        map.result.first().requestIp == '127.0.0.1'
    }

    void "can't insert a build but ends without error"() {
        given:
        SurrealPersistenceService storage = applicationContext.getBean(SurrealPersistenceService)
        BuildRecord build = new BuildRecord(
                buildId: 'test',
                dockerFile: 'test',
                condaFile: 'test',
                targetImage: 'test',
                userName: 'test',
                userEmail: 'test',
                userId: 1,
                requestIp: '127.0.0.1',
                startTime: Instant.now(),
                duration: Duration.ofSeconds(1),
                exitStatus: 0 )

        when:
        surrealContainer.stop()

        storage.saveBuild(build)

        sleep 100 //as we are using async, let database a while to store the item
        then:
        true
    }

    void "an event insert a build"() {
        given:
        HttpClient httpClient = HttpClient.create(new URL(surrealDbURL))
        def storage = applicationContext.getBean(SurrealPersistenceService)
        storage.initializeDb()
        final service = applicationContext.getBean(SurrealPersistenceService)
        BuildRequest request = BuildRequest
                .builder()
                .withId('100')
                .withWorkDir(Path.of('/some/dir'))
                .withPlatform(ContainerPlatform.of('amd64'))
                .withRequestIp("127.0.0.1")
                .build()
        and:
        BuildResult result = new BuildResult(request.id, 0, "content", Instant.now(), Duration.ofSeconds(1))
        BuildEvent event = new BuildEvent(request, result)

        when:
        service.onBuildEvent(event)
        sleep 100 //as we are using async, let database a while to store the item
        then:
        def map = httpClient.toBlocking()
                .retrieve(
                        HttpRequest.GET("/key/wave_build")
                                .headers([
                                        'ns'          : 'test',
                                        'db'          : 'test',
                                        'User-Agent'  : 'micronaut/1.0',
                                        'Accept': 'application/json'])
                                .basicAuth('root', 'root'), Map<String, Object>)
        map.result.size()
        map.result.first().requestIp == '127.0.0.1'
    }

    void "an event is not inserted if no database"() {
        given:
        surrealContainer.stop()
        final service = applicationContext.getBean(SurrealPersistenceService)
        BuildRequest request = BuildRequest
                .builder()
                .withId('100')
                .withWorkDir(Path.of('/some/dir'))
                .withPlatform(ContainerPlatform.of('amd64'))
                .withRequestIp("127.0.0.1")
                .build()
        and:
        BuildResult result = new BuildResult(request.id, 0, "content", Instant.now(), Duration.ofSeconds(1))
        BuildEvent event = new BuildEvent(request, result)

        when:
        service.onBuildEvent(event)
        sleep 100 //as we are using async, let database a while to store the item
        then:
        true
    }

    def 'should load a build record' () {
        given:
        final persistence = applicationContext.getBean(SurrealPersistenceService)
        final request = BuildRequest
                .builder()
                .withId('100')
                .withDockerFile('FROM foo:latest')
                .withWorkDir(Path.of('/some/dir'))
                .withCondaFile('bioconda:foo')
                .withPlatform(ContainerPlatform.of('amd64'))
                .withRequestIp("127.0.0.1")
                .build()
        and:
        final result = new BuildResult(request.id, -1, "ok", Instant.now(), Duration.ofSeconds(3))
        final event = new BuildEvent(request, result)
        final record = BuildRecord.fromEvent(event)

        and:
        persistence.saveBuildBlocking(record)

        when:
        def loaded = persistence.loadBuild(record.buildId)
        then:
        loaded == record
    }

    def 'should patch duration field' () {
        expect:
        SurrealPersistenceService.patchDuration('foo') == 'foo'
        SurrealPersistenceService.patchDuration('"duration":3.00') == '"duration":3.00'
        SurrealPersistenceService.patchDuration('"duration":"3.00"') == '"duration":3.00'
        SurrealPersistenceService.patchDuration('aaa,"duration":"300.1234",zzz') == 'aaa,"duration":300.1234,zzz'
    }

    @Ignore
    void "should insert conda record"() {
        given:
        def httpClient = HttpClient.create(new URL(surrealDbURL))
        def storage = applicationContext.getBean(SurrealPersistenceService)
        and:
        storage.initializeDb()

        when:
        storage.saveConda(new CondaRecord(id:'100', lockFile: 'LOCK X'))
        sleep 100 //as we are using async, let database a while to store the item
        then:
        storage.loadConda('100').lockFile == 'LOCK X'
    }
}
