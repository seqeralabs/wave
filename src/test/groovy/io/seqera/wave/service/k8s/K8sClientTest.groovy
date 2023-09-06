/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
                ['cat','/kaniko/.docker/config.json'],
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
