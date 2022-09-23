package io.seqera.wave.service.builder

import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files

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
        def dockerfile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerfile, buildRepo, cacheRepo, null, null)
        def REQ = new BuildRequest(dockerfile, folder, buildRepo, null, Mock(User), ContainerPlatform.of('amd64'),cfg, cacheRepo, "")

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
        def dockerfile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def creds = credentialsProvider.getDefaultCredentials('docker.io')
        log.debug "Using docker credentials=$creds"
        and:
        buildRepo = "docker.io/$creds.username/wave-tests"
        and:
        def cfg = dockerAuthService.credentialsConfigJson(dockerfile, buildRepo, null, null, null)
        def REQ = new BuildRequest(dockerfile, folder, buildRepo, null, Mock(User), ContainerPlatform.of('amd64'),cfg, null, null)

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
        def dockerfile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def creds = credentialsProvider.getDefaultCredentials('quay.io')
        log.debug "Using docker credentials=$creds"
        and:
        buildRepo = "quay.io/pditommaso/wave-tests"
        def cfg = dockerAuthService.credentialsConfigJson(dockerfile, buildRepo, null, null, null)
        def REQ = new BuildRequest(dockerfile, folder, buildRepo, null, Mock(User), ContainerPlatform.of('amd64'),cfg, null, "")

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
        def dockerfile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        buildRepo = "${System.getenv('AZURECR_USER')}.azurecr.io/wave-tests"
        cacheRepo = buildRepo
        def cfg = dockerAuthService.credentialsConfigJson(dockerfile, buildRepo, cacheRepo, null, null)
        def REQ = new BuildRequest(dockerfile, folder, buildRepo, null, Mock(User), ContainerPlatform.of('amd64'),cfg, cacheRepo, "")

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
}
