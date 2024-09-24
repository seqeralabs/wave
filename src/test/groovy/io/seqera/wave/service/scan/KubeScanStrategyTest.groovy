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

package io.seqera.wave.service.scan

import spock.lang.Specification

import java.nio.file.Files

import io.micronaut.context.annotation.Property
import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.k8s.K8sService
import io.seqera.wave.service.k8s.K8sServiceImpl
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Property(name="wave.build.k8s.namespace",value="foo")
@Property(name="wave.build.k8s.configPath",value="/home/kube.config")
@Property(name="wave.build.k8s.storage.claimName",value="bar")
@Property(name="wave.build.k8s.storage.mountPath",value="/build")
@Property(name='wave.build.k8s.node-selector[linux/amd64]',value="service=wave-scan")
@Property(name='wave.build.k8s.node-selector[linux/arm64]',value="service=wave-scan-arm64")
class KubeScanStrategyTest extends Specification {

    @Inject
    KubeScanStrategy strategy

    @Inject
    ScanConfig scanConfig

    @Inject
    K8sService k8sService

    @MockBean(K8sServiceImpl)
    K8sService k8sService(){
        Mock(K8sService)
    }


    def "request to scan a container with right selector"(){
        given:
        def folder = Files.createTempDirectory('test')

        when:
        def request = new ScanRequest('100', 'abc', null, 'ubuntu', ContainerPlatform.of('amd64'), folder.resolve('foo'))
        Files.createDirectories(request.workDir)

        strategy.scanContainer('job-name', request)
        then:
        1 * k8sService.launchScanJob(_, _, _, _, _, _, [service:'wave-scan']) >> null

        when:
        def request2 = new ScanRequest('100', 'abc', null, 'ubuntu', ContainerPlatform.of('arm64'), folder.resolve('bar'))
        Files.createDirectories(request.workDir)

        strategy.scanContainer('job-name', request2)

        then:
        1 * k8sService.launchScanJob(_, _, _, _, _, _, [service:'wave-scan-arm64']) >> null

        cleanup:
        folder?.deleteDir()
    }
}
