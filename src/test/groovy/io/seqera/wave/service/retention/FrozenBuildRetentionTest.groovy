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

package io.seqera.wave.service.retention

import spock.lang.Specification

import io.seqera.wave.controller.ContainerController
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.tower.PlatformId
/**
 * Retention may remove the build record of a frozen container. Wave resolves a cached build from
 * a registry digest probe and a target image name computed from the request, so the reuse does not
 * depend on that record - these tests pin that property, which is why retention needs no separate
 * input -> target mapping.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class FrozenBuildRetentionTest extends Specification {

    def 'should resolve a frozen build from the registry when its record has been purged'() {
        given:
        def target = 'docker.io/foo/bar:latest'
        def proxy = Mock(RegistryProxyService)
        def persistence = Mock(PersistenceService)
        def buildService = Mock(ContainerBuildService)
        def controller = new ContainerController(
                registryProxyService: proxy,
                persistenceService: persistence,
                buildService: buildService )
        def build = Mock(BuildRequest) {
            getTargetImage() >> target
            getIdentity() >> PlatformId.NULL
        }

        when:
        def track = controller.checkBuild(build, false)

        then: 'the image is still in the registry'
        1 * proxy.getImageDigestOrUnavailable(target, PlatformId.NULL) >> 'sha256:12345'
        and: 'but the build record is gone'
        1 * persistence.loadBuildSucceed(target, 'sha256:12345') >> null
        and: 'no rebuild is triggered'
        0 * buildService.buildImage(_)
        and: 'the existing target image is returned as a cached, successful build'
        track.targetImage == target
        track.cached
        track.succeeded
        and: 'only the build id is lost'
        track.id == null
    }

    def 'should rebuild when the image is absent from the registry'() {
        given:
        def target = 'docker.io/foo/bar:latest'
        def proxy = Mock(RegistryProxyService)
        def persistence = Mock(PersistenceService)
        def buildService = Mock(ContainerBuildService)
        def controller = new ContainerController(
                registryProxyService: proxy,
                persistenceService: persistence,
                buildService: buildService )
        def build = Mock(BuildRequest) {
            getTargetImage() >> target
            getIdentity() >> PlatformId.NULL
        }

        when:
        controller.checkBuild(build, false)

        then: 'a purged record is not what decides a rebuild - the missing image is'
        1 * proxy.getImageDigestOrUnavailable(target, PlatformId.NULL) >> null
        0 * persistence.loadBuildSucceed(_, _)
        1 * buildService.buildImage(build)
    }

}
