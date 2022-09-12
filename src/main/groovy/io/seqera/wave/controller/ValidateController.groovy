package io.seqera.wave.controller

import javax.validation.Valid

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.seqera.wave.api.ServiceInfoResponse
import io.seqera.wave.auth.RegistryAuthService
import io.swagger.annotations.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import jakarta.inject.Inject
import reactor.core.publisher.Mono

@Controller("/validate-creds")
class ValidateController {

    @Inject RegistryAuthService loginService

    @Post
    @Operation(summary = "Validate credentials",
            operationId = "validate-creds",
            description = "Validate the credentials of the user against the login service")
    @ApiResponse(code = 200, response = Boolean)
    Mono<Boolean> validateCreds(@Valid ValidateRegistryCredsRequest request){
        Mono.just(
            loginService.validateUser(request.registry, request.userName, request.password)
        )

    }

}
