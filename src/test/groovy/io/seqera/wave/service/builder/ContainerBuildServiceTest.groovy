package io.seqera.wave.service.builder

import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files
import java.time.Duration

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.DockerAuthService
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@MicronautTest
class ContainerBuildServiceTest extends Specification {

    @Inject ContainerBuildServiceImpl service
    @Inject RegistryLookupService lookupService
    @Inject RegistryCredentialsProvider credentialsProvider
    @Inject DockerAuthService dockerAuthService

    @Value('${wave.build.repo}') String buildRepo
    @Value('${wave.build.cache}') String cacheRepo


    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should build & push container to aws' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, cacheRepo, null, null,null,null)
        def REQ = new BuildRequest(dockerFile, folder, buildRepo, null, null, Mock(User), ContainerPlatform.of('amd64'),cfg, cacheRepo, "")

        when:
        def result = service.launch(REQ)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }

    @Requires({System.getenv('DOCKER_USER') && System.getenv('DOCKER_PAT')})
    def 'should build & push container to docker.io' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        buildRepo = "docker.io/pditommaso/wave-tests"
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, null, null,null,null)
        def REQ = new BuildRequest(dockerFile, folder, buildRepo, null, null, Mock(User), ContainerPlatform.of('amd64'),cfg, null, null)

        when:
        def result = service.launch(REQ)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }

    @Requires({System.getenv('QUAY_USER') && System.getenv('QUAY_PAT')})
    def 'should build & push container to quay.io' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        buildRepo = "quay.io/pditommaso/wave-tests"
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, null, null, null, null)
        def REQ = new BuildRequest(dockerFile, folder, buildRepo, null, null, Mock(User), ContainerPlatform.of('amd64'),cfg, null, "")

        when:
        def result = service.launch(REQ)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }

    @Requires({System.getenv('AZURECR_USER') && System.getenv('AZURECR_PAT')})
    def 'should build & push container to azure' () {
        given:
        def folder = Files.createTempDirectory('test')
        and:
        def dockerFile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        buildRepo = "seqeralabs.azurecr.io/wave-tests"
        def cfg = dockerAuthService.credentialsConfigJson(dockerFile, buildRepo, null, null, null, null, null)
        def REQ = new BuildRequest(dockerFile, folder, buildRepo, null, null, Mock(User), ContainerPlatform.of('amd64'),cfg, null, "")

        when:
        def result = service.launch(REQ)
        and:
        println result.logs
        then:
        result.id
        result.startTime
        result.duration
        result.exitStatus == 0

        cleanup:
        folder?.deleteDir()
    }

    def 'should save build docker build file' () {
        given:
        def folder = Files.createTempDirectory('test')
        def DURATION = Duration.ofDays(1)
        and:
        def cfg = 'some credentials'
        def dockerFile = '''
                FROM busybox
                RUN echo Hello > hello.txt
                '''.stripIndent()
        and:
        def condaFile = '''
                this is
                the conda
                recipe
                '''
        and:
        def spackFile = '''
                this is
                the spack
                build file
                '''
        and:
        def REQ = new BuildRequest(dockerFile, folder, 'box:latest', condaFile, spackFile, Mock(User), ContainerPlatform.of('amd64'), cfg, null, "")
        and:
        def store = Mock(BuildStore)
        def strategy = Mock(BuildStrategy)
        def builder = new ContainerBuildServiceImpl(buildStrategy: strategy, buildStore: store, statusDuration: DURATION)
        def RESPONSE = Mock(BuildResult)

        when:
        def result = builder.launch(REQ)
        then:
        1 * strategy.build(REQ) >> RESPONSE
        1 * store.storeBuild(REQ.targetImage, RESPONSE, DURATION) >> null
        and:
        REQ.workDir.resolve('Dockerfile').text == dockerFile
        REQ.workDir.resolve('conda.yml').text == condaFile
        REQ.workDir.resolve('spack.yml').text == spackFile
        and:
        result == RESPONSE

        cleanup:
        folder?.deleteDir()
    }
}
