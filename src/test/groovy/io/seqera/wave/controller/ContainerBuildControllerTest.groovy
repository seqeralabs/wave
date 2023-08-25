package io.seqera.wave.controller

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveBuildRecord
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerBuildControllerTest extends Specification {
    @Inject
    @Client("/")
    HttpClient client

    @Inject PersistenceService persistenceService

    def 'should get container build record' () {
        given:
        final build = new BuildRequest(
                'FROM foo:latest',
                Path.of("/some/path"),
                "buildrepo",
                'conda::recipe',
                'some-spack-recipe',
                BuildFormat.DOCKER,
                null,
                null,
                null,
                ContainerPlatform.of('amd64'),
                '{auth}',
                'docker.io/my/repo',
                '12345',
                "1.2.3.4",
                null )
        final result = new BuildResult(build.id, -1, "ok", Instant.now(), Duration.ofSeconds(3))
        final event = new BuildEvent(build, result)
        final entry = WaveBuildRecord.fromEvent(event)
        and:
        persistenceService.saveBuild(entry)

        when:
        def req = HttpRequest.GET("/v1alpha1/builds/${build.id}")
        def res = client.toBlocking().exchange(req, WaveBuildRecord)

        then:
        res.body().buildId == build.id
    }
}
