package io.seqera.wave.controller

import groovy.transform.CompileStatic
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
/**
 * Implements a controller for openapi
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
class OpenAPIController {
    @Get(uri = "/openapi")
    HttpResponse getOpenAPI() {
    println("Redirecting to /openapi/")
        HttpResponse.redirect(URI.create("/openapi/"))
    }
}
