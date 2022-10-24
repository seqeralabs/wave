package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.hateoas.JsonError
import io.seqera.wave.ErrorHandler
import jakarta.inject.Inject
/**
 * Handle application errors
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller('/error')
class ErrorController {

    @Inject ErrorHandler handler

    @Error(global = true)
    HttpResponse<JsonError> handleException(HttpRequest request, Throwable exception) {
        handler.handle(request, exception, (msg, id) -> { return new JsonError(msg) })
    }
    
}
