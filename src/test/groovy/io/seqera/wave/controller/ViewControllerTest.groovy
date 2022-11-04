package io.seqera.wave.controller

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.persistence.BuildRecord
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ViewControllerTest extends Specification {

    @Inject
    @Client("/")
    HttpClient client

    @Inject
    PersistenceService persistenceService

    def 'should render build page' () {
        given:
        def controller = new ViewController(serverUrl: 'http://foo.com')
        and:
        def record = new BuildRecord(
                buildId: '12345',
                dockerFile: 'FROM foo',
                condaFile: 'conda::foo',
                targetImage: 'docker.io/some:image',
                userName: 'paolo',
                userEmail: 'paolo@seqera.io',
                userId: 100,
                requestIp: '10.20.30.40',
                startTime: Instant.now(),
                offsetId: '+02:00',
                duration: Duration.ofMinutes(1),
                exitStatus: 0,
                platform: 'linux/amd64' )
        when:
        def page = controller.renderBuildView(record)
        then:
        page
    }

    def 'should render a build page' () {
        given:
        def record1 = new BuildRecord(
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
        persistenceService.saveBuild(record1)
        and:
        def request = HttpRequest.GET("/view/builds/${record1.buildId}")
        def response = client.toBlocking().exchange(request, String)
        new File("build/${record1.buildId}.html").text = response.body()
        then:
        response.body().contains(record1.buildId)
    }
}
