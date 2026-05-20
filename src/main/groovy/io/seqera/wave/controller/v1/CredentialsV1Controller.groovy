/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller.v1

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.annotation.Controller
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.api.v1.model.ValidateRegistryCredsRequest
import io.seqera.wave.api.v1.spec.CredentialsApiSpec
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.configuration.SsrfConfig
import io.seqera.wave.util.SsrfValidator
import jakarta.inject.Inject

@Slf4j
@CompileStatic
@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
class CredentialsV1Controller implements CredentialsApiSpec {

    @Inject
    RegistryAuthService registryAuthService

    @Inject
    SsrfConfig ssrfConfig

    @Override
    Boolean validate(ValidateRegistryCredsRequest validateRegistryCredsRequest) {
        if (ssrfConfig.ssrfProtectionEnabled) {
            SsrfValidator.validateHost(validateRegistryCredsRequest.getRegistry())
        }
        return registryAuthService.validateUser(
                validateRegistryCredsRequest.getRegistry(),
                validateRegistryCredsRequest.getUserName(),
                validateRegistryCredsRequest.getPassword()
        )
    }
}
