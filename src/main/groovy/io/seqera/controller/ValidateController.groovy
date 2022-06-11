package io.seqera.controller

import javax.validation.Valid

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.seqera.docker.ContainerService
import reactor.core.publisher.Mono

@Controller("/validate-creds")
class ValidateController {

    ContainerService containerService

    ValidateController(ContainerService containerService) {
        this.containerService = containerService
    }

    @Post
    Mono<Boolean> validateCreds(@Valid ValidateRegistryCredsRequest request){
        Mono.just(
            containerService.validateUser(request.registry, request.userName, request.password)
        )

    }

}
