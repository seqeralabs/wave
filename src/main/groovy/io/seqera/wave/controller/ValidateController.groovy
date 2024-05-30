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

import javax.validation.Valid

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.auth.RegistryAuthService
import jakarta.inject.Inject
import reactor.core.publisher.Mono

@ExecuteOn(TaskExecutors.IO)
@Controller("/validate-creds")
class ValidateController {

    @Inject RegistryAuthService loginService

    @Post
    Mono<Boolean> validateCreds(@Valid ValidateRegistryCredsRequest request){
        Mono.just(
            loginService.validateUser(request.registry, request.userName, request.password)
        )
    }

}
