/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.service.inclusion

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.spec.ConfigSpec
import io.seqera.wave.core.spec.ContainerSpec
import io.seqera.wave.core.spec.ManifestSpec
import io.seqera.wave.core.spec.ObjectRef
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class ContainerInclusionImplTest extends Specification {
    @Inject
    ContainerInspectService inspectService

    def "addContainerInclusions should add layers to request when container names are provided"() {
        given:
        def service = new ContainerInclusionImpl(inspectService: inspectService)
        def request = new SubmitContainerTokenRequest(containerIncludes: ["alpine@sha256:beefdbd8a1da6d2915566fde36db9db0b524eb737fc57cd1367effd16dc0d06d",
        "busybox@sha256:c230832bd3b0be59a6c47ed64294f9ce71e91b327957920b6929a0caa8353140"], containerPlatform: "linux/amd64")
        def identity = PlatformId.NULL

        when:
        def result = service.addContainerInclusions(request, identity)

        then:
        result.containerConfig.layers.size() == 2
        result.containerConfig.layers[0].location == "docker://docker.io/v2/library/alpine/blobs/sha256:43c4264eed91be63b206e17d93e75256a6097070ce643c5e8f0379998b44f170"
        result.containerPlatform == "linux/amd64"
        result.containerConfig.layers[1].location == "docker://docker.io/v2/library/busybox/blobs/sha256:2fce1e0cdfc5e77c450679c5cce031e1da81ec99eee897bec1b0faf76d51f574"
    }

    def "addContainerInclusions should throw BadRequestException when more than 10 container names are provided"() {
        given:
        def service = new ContainerInclusionImpl(inspectService: inspectService)
        def request = new SubmitContainerTokenRequest(containerIncludes: (1..11).collect { "container$it" })
        def identity = PlatformId.NULL

        when:
        service.addContainerInclusions(request, identity)

        then:
        thrown(BadRequestException)
    }
}
