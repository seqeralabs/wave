package io.seqera.wave.controller

import javax.validation.Valid

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.seqera.wave.auth.RegistryAuthService
import jakarta.inject.Inject
import reactor.core.publisher.Mono

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
