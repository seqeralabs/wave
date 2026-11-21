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

package io.seqera.wave.controller

import spock.lang.Specification

import io.seqera.wave.api.PackagesSpec
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.validation.ValidationService
import io.seqera.wave.tower.PlatformId

/**
 * Lightweight unit tests for the freeze-mode validation in {@link ContainerController}.
 *
 * These tests construct the controller directly with mocked collaborators, so they do not
 * boot the full Micronaut application context.
 */
class FreezeValidationTest extends Specification {

    private ContainerController freezeController(boolean defaultPublicRepository = false) {
        def config = new BuildConfig()
        if( defaultPublicRepository ) {
            config.defaultPublicRepository = 'community.seqera.io'
        }
        return new ContainerController(
                validationService: Mock(ValidationService) { isCustomRepo(_) >> false },
                buildConfig: config,
                allowAnonymous: true)
    }

    def 'freezing a Dockerfile without a custom build repository returns a descriptive message'() {
        given:
        def controller = freezeController()

        when: 'v1 request'
        controller.handleRequest(null, new SubmitContainerTokenRequest(containerFile: 'FROM foo', freeze: true), PlatformId.NULL, false)
        then:
        def err1 = thrown(BadRequestException)
        err1.message == "Attribute `buildRepository` must be specified when freezing a Dockerfile: only Conda and pip packages can be frozen to the default Seqera Containers repository"

        when: 'v2 request'
        controller.handleRequest(null, new SubmitContainerTokenRequest(containerFile: 'FROM foo', freeze: true), PlatformId.NULL, true)
        then:
        def err2 = thrown(BadRequestException)
        err2.message == "Attribute `buildRepository` must be specified when freezing a Dockerfile: only Conda and pip packages can be frozen to the default Seqera Containers repository"
    }

    def 'freezing an existing container image without a custom build repository returns a descriptive message'() {
        given:
        def controller = freezeController()

        when: 'v1 request'
        controller.handleRequest(null, new SubmitContainerTokenRequest(containerImage: 'alpine', freeze: true), PlatformId.NULL, false)
        then:
        def err1 = thrown(BadRequestException)
        err1.message == "Attribute `buildRepository` must be specified when freezing an existing container image: only Conda and pip packages can be frozen to the default Seqera Containers repository"

        when: 'v2 request'
        controller.handleRequest(null, new SubmitContainerTokenRequest(containerImage: 'alpine', freeze: true), PlatformId.NULL, true)
        then:
        def err2 = thrown(BadRequestException)
        err2.message == "Attribute `buildRepository` must be specified when freezing an existing container image: only Conda and pip packages can be frozen to the default Seqera Containers repository"
    }

    def 'freezing packages without a custom build repository or a default public repository returns a descriptive message'() {
        given:
        def controller = freezeController()

        when:
        controller.handleRequest(null, new SubmitContainerTokenRequest(packages: new PackagesSpec(), freeze: true), PlatformId.NULL, true)
        then:
        def e = thrown(BadRequestException)
        e.message == "Attribute `buildRepository` must be specified when freezing packages: no default public repository is configured in this Wave deployment"
    }
}
