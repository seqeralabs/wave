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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.auth.RegistryAuthService
import io.seqera.wave.util.SsrfValidator
import jakarta.inject.Inject
import jakarta.validation.Valid

@Slf4j
@CompileStatic
@Controller("/")
@ExecuteOn(TaskExecutors.BLOCKING)
class ValidateController {

    @Inject RegistryAuthService loginService

    @Value('${wave.security.ssrf-protection.enabled:true}')
    Boolean ssrfProtectionEnabled

    @Deprecated
    @Post("/validate-creds")
    Boolean validateCreds(@Valid ValidateRegistryCredsRequest request){
        // Validate registry to prevent SSRF attacks
        if (ssrfProtectionEnabled && request.registry) {
            log.debug "SSRF protection enabled, validating registry: ${request.registry}"
            SsrfValidator.validateHost(request.registry)
        } else if (!ssrfProtectionEnabled) {
            log.warn "SSRF protection is DISABLED - allowing registry: ${request.registry}"
        }
        loginService.validateUser(request.registry, request.userName, request.password)
    }

    @Post("/v1alpha2/validate-creds")
    Boolean validateCredsV2(@Valid @Body ValidateRegistryCredsRequest request){
        // Validate registry to prevent SSRF attacks
        if (ssrfProtectionEnabled && request.registry) {
            log.debug "SSRF protection enabled, validating registry: ${request.registry}"
            SsrfValidator.validateHost(request.registry)
        } else if (!ssrfProtectionEnabled) {
            log.warn "SSRF protection is DISABLED - allowing registry: ${request.registry}"
        }
        loginService.validateUser(request.registry, request.userName, request.password)
    }

}
