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

package io.seqera.wave.service.inspect

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.auth.RegistryCredentialsProvider
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class ContainerInspectServiceImplTest extends Specification {

    @Inject RegistryCredentialsProvider credentialsProvider
    @Inject ContainerInspectServiceImpl service

    def 'should create creds json file' () {
        given:
        def creds = credentialsProvider.getDefaultCredentials('docker.io')
        def EXPECTED = "$creds.username:$creds.password".bytes.encodeBase64()
        when:
        def result = service.credsJson(['docker.io'] as Set, PlatformId.NULL)
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
        def result = service.credsJson(['docker.io/busybox','quay.io/alpine'] as Set, PlatformId.NULL)
        then:
        // note: the auth below depends on the docker user and password used for test
        result == """{"auths":{"https://index.docker.io/v1/":{"auth":"$EXPECTED1"},"https://quay.io":{"auth":"$EXPECTED2"}}}"""
    }

    def 'should remove duplicates' () {
        given:
        def creds1 = credentialsProvider.getDefaultCredentials('docker.io')
        def EXPECTED1 = "$creds1.username:$creds1.password".bytes.encodeBase64()
        when:
        def result = service.credsJson(['docker.io/busybox','docker.io/ubuntu:latest'] as Set, PlatformId.NULL)
        then:
        // note: the auth below depends on the docker user and password used for test
        result == """{"auths":{"https://index.docker.io/v1/":{"auth":"$EXPECTED1"}}}"""

        when:
        result = service.credsJson(['busybox','docker.io/ubuntu:latest'] as Set, PlatformId.NULL)
        then:
        // note: the auth below depends on the docker user and password used for test
        result == """{"auths":{"https://index.docker.io/v1/":{"auth":"$EXPECTED1"}}}"""
    }

    def 'should find repos' () {

        expect:
        ContainerInspectServiceImpl.findRepositories() == []

        and:
        ContainerInspectServiceImpl.findRepositories('FROM ubuntu:latest')  == ['ubuntu:latest']

        and:
        ContainerInspectServiceImpl.findRepositories('FROM --platform=amd64 quay.io/ubuntu:latest')  == ['quay.io/ubuntu:latest']

        and:
        ContainerInspectServiceImpl.findRepositories('''
                FROM gcr.io/kaniko-project/executor:latest AS knk
                RUN this and that
                FROM amazoncorretto:17.0.4
                COPY --from=knk /kaniko/executor /kaniko/executor
                ''') == ['gcr.io/kaniko-project/executor:latest', 'amazoncorretto:17.0.4']

    }

    def 'should fetch container entry point' () {
        given:
        def DOCKERFILE = 'FROM busybox'
        when:
        service.containerEntrypoint(DOCKERFILE, PlatformId.NULL)
        then:
        noExceptionThrown()
    }

    def 'should fetch container manifest for legacy image' () {
        given:
        def DOCKERFILE = 'FROM quay.io/biocontainers/fastqc:0.11.9--0'
        when:
        service.containerEntrypoint(DOCKERFILE, PlatformId.NULL)
        then:
        noExceptionThrown()
    }

    def 'should inspect containerfile entrypoint and repository' () {
        when:
        def DOCKERFILE = '''
            FROM ubuntu:latest
            RUN this 
            RUN THAT
            ENTRYPOINT this --that
            '''
        and:
        def result = ContainerInspectServiceImpl.inspectItems(DOCKERFILE)
        then:
        // capture both the repository name and the explicity entrypoint
        // the entrypoint is returned first because it has higher priority
        result == [
                new ContainerInspectServiceImpl.InspectEntrypoint(["this","--that"]),
                new ContainerInspectServiceImpl.InspectRepository("ubuntu:latest")
        ]

        when:
        DOCKERFILE = '''
            FROM ubuntu:latest
            RUN this 
            RUN THAT
            ENTRYPOINT this --that
            
            FROM debian:latest
            RUN one --more
            '''
        and:
        result = ContainerInspectServiceImpl.inspectItems(DOCKERFILE)
        then:
        // return only the repository name
        // because as multi-stage build, the entries in the previous statement should not
        // affect the final entrypoint
        result == [
                new ContainerInspectServiceImpl.InspectRepository("debian:latest")
        ]
    }
}
