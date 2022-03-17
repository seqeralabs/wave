package io.seqera.controller

import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Head
import org.reactivestreams.Publisher
import reactor.core.publisher.Mono

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Controller("/ping")
class PingController {

    @Get
    @Head
    Publisher<String> ping(){
        return Mono.just("pong");
    }

}
