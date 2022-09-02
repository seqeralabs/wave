package io.seqera.wave.service.builder

import spock.lang.Requires
import spock.lang.Specification

import java.nio.file.Files

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.auth.RegistryLookupService
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerBuildServiceTest extends Specification {

    @Inject ContainerBuildServiceImpl service
    @Inject RegistryLookupService lookupService
    @Inject RegistryCredentialsProvider credentialsProvider

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should build & push container' () {
        given:
        def folder = Files.createTempDirectory('test')
        def repo = '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/build'
        and:
        def dockerfile = '''
        FROM busybox
        RUN echo Hello > hello.txt
        '''.stripIndent()
        and:
        def REQ = new BuildRequest(dockerfile, folder, repo, null, Mock(User), 'amd64')

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

    def 'should create creds json file' () {
        given:
        def creds = credentialsProvider.getCredentials('docker.io')
        def EXPECTED = "$creds.username:$creds.password".bytes.encodeBase64()
        when:
        def result = service.credsJson(['docker.io'] as Set)
        then:
        // note: the auth below depends on the docker user and password used for test
        result == """{"auths":{"https://registry-1.docker.io":{"auth":"$EXPECTED"}}}"""
    }

    def 'should create creds json file with more registries' () {
        given:
        def creds1 = credentialsProvider.getCredentials('docker.io')
        def EXPECTED1 = "$creds1.username:$creds1.password".bytes.encodeBase64()
        and:
        def creds2 = credentialsProvider.getCredentials('quay.io')
        def EXPECTED2 = "$creds2.username:$creds2.password".bytes.encodeBase64()
        when:
        def result = service.credsJson(['docker.io','quay.io'] as Set)
        then:
        // note: the auth below depends on the docker user and password used for test
        result == """{"auths":{"https://registry-1.docker.io":{"auth":"$EXPECTED1"},"https://quay.io":{"auth":"$EXPECTED2"}}}"""
    }

    def 'should find repos' () {

        expect:
        ContainerBuildServiceImpl.findRepositories() == [] as Set
        
        and:
        ContainerBuildServiceImpl.findRepositories('from ubuntu:latest')  == ['docker.io'] as Set

        and:
        ContainerBuildServiceImpl.findRepositories('FROM quay.io/ubuntu:latest')  == ['quay.io'] as Set

        and:
        ContainerBuildServiceImpl.findRepositories('''
                FROM gcr.io/kaniko-project/executor:latest AS knk
                RUN this and that
                FROM amazoncorretto:17.0.4
                COPY --from=knk /kaniko/executor /kaniko/executor
                ''') == ['gcr.io', 'docker.io'] as Set

    }
}
