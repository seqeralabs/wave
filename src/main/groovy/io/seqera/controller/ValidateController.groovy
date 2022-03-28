package io.seqera.controller

import javax.validation.Valid

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import reactor.core.publisher.Mono

@Controller("/validate-creds")
class ValidateController {


    @Post
    Mono<String> validateCreds(  @Valid ValidateContainerRegistryCreds request){

        Mono.just("Ok")
    }

}
