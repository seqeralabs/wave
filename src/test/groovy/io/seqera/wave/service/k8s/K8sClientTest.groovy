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

package io.seqera.wave.service.k8s

import spock.lang.Ignore
import spock.lang.Specification

import java.nio.file.Path

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Ignore
@MicronautTest
class K8sClientTest extends Specification {

    @Inject K8sService k8sService

    def 'should create job' () {
        when:
        def job = k8sService.createJob('foo-2', 'busybox', ['sh', '-c', 'slep 10'])
        println job
        then:
        job
    }

    def 'should get job' () {
        when:
        def job = k8sService.getJob('foo-2')
        job.status.succeeded == 1
        then:
        true
    }

    def 'should create pod' () {
        when:
        def pod = k8sService.buildContainer(
                'my-pod',
                'busybox',
                ['cat','/home/user/.docker/config.json'],
                Path.of('/work/dir'),
                Path.of('/creds'),
                Path.of('/spack/dir'),
                ['my-creds': 'selector'])
        then:
        true

        when:
        def str = k8sService.logsPod('my-pod')
        then:
        str

    }
}
