package io.seqera.wave.auth

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class DockerAuthServiceTest extends Specification {

    @Inject RegistryCredentialsProvider credentialsProvider
    @Inject DockerAuthService service

    def 'should create creds json file' () {
        given:
        def creds = credentialsProvider.getDefaultCredentials('docker.io')
        def EXPECTED = "$creds.username:$creds.password".bytes.encodeBase64()
        when:
        def result = service.credsJson(['docker.io'] as Set, null, null)
        then:
        // note: the auth below depends on the docker user and password used for test
        result == """{"auths":{"https://index.docker.io/v1/":{"auth":"$EXPECTED"}}}"""
    }

    def 'should create creds json file with more registries' () {
        given:
        def creds1 = credentialsProvider.getDefaultCredentials('docker.io')
        def EXPECTED1 = "$creds1.username:$creds1.password".bytes.encodeBase64()
        and:
        def creds2 = credentialsProvider.getDefaultCredentials('quay.io')
        def EXPECTED2 = "$creds2.username:$creds2.password".bytes.encodeBase64()
        when:
        def result = service.credsJson(['docker.io/busybox','quay.io/alpine'] as Set, null, null)
        then:
        // note: the auth below depends on the docker user and password used for test
        result == """{"auths":{"https://index.docker.io/v1/":{"auth":"$EXPECTED1"},"https://quay.io":{"auth":"$EXPECTED2"}}}"""
    }


    def 'should find repos' () {

        expect:
        DockerAuthService.findRepositories() == [] as Set

        and:
        DockerAuthService.findRepositories('from ubuntu:latest')  == ['ubuntu:latest'] as Set

        and:
        DockerAuthService.findRepositories('FROM quay.io/ubuntu:latest')  == ['quay.io/ubuntu:latest'] as Set

        and:
        DockerAuthService.findRepositories('''
                FROM gcr.io/kaniko-project/executor:latest AS knk
                RUN this and that
                FROM amazoncorretto:17.0.4
                COPY --from=knk /kaniko/executor /kaniko/executor
                ''') == ['gcr.io/kaniko-project/executor:latest', 'amazoncorretto:17.0.4'] as Set

    }
}
