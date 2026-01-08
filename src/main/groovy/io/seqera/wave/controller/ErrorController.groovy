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
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Error
import io.micronaut.http.hateoas.JsonError
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.ErrorHandler
import io.seqera.wave.api.ErrorResponse
import io.seqera.random.LongRndKey
import com.fasterxml.jackson.core.JsonProcessingException
import com.fasterxml.jackson.databind.JsonMappingException
import io.micronaut.json.JsonSyntaxException
import io.micronaut.core.convert.exceptions.ConversionErrorException
import jakarta.validation.ConstraintViolationException
import jakarta.inject.Inject
/**
 * Handle application errors
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller('/error')
@ExecuteOn(TaskExecutors.BLOCKING)
class ErrorController {

    @Inject
    private ErrorHandler handler

    /**
     * Create a sanitized error response with logging
     *
     * @param request The HTTP request
     * @param exception The exception that occurred
     * @param errorType Description of error type for logging
     * @param message User-friendly error message
     * @param status HTTP status to return
     * @return Sanitized error response
     */
    private static HttpResponse<ErrorResponse> createSanitizedErrorResponse(
            HttpRequest request,
            Throwable exception,
            String errorType,
            String message,
            HttpStatus status = HttpStatus.BAD_REQUEST) {

        final errId = LongRndKey.rndHex()
        final path = request?.path ?: "unknown"

        log.error("${errorType} - Request ID: ${errId} - Path: ${path}", exception)

        return HttpResponse.status(status).body(
            new ErrorResponse(
                message,
                status.reason,
                status.code,
                path,
                errId
            )
        )
    }

    /**
     * Handle JSON processing/parsing errors with sanitized response
     */
    @Error(global = true, exception = JsonProcessingException.class)
    static HttpResponse<ErrorResponse> handleJsonError(HttpRequest request, JsonProcessingException e) {
        return createSanitizedErrorResponse(
            request,
            e,
            "JSON processing error",
            "Invalid request format. Please check your input and try again."
        )
    }

    /**
     * Handle JSON mapping/deserialization errors with sanitized response
     */
    @Error(global = true, exception = JsonMappingException.class)
    static HttpResponse<ErrorResponse> handleJsonMappingError(HttpRequest request, JsonMappingException e) {
        return createSanitizedErrorResponse(
            request,
            e,
            "JSON mapping error",
            "Invalid request format. Please check your input and try again."
        )
    }

    /**
     * Handle JSON syntax errors (Micronaut-specific) with sanitized response
     */
    @Error(global = true, exception = JsonSyntaxException.class)
    static HttpResponse<ErrorResponse> handleJsonSyntaxError(HttpRequest request, JsonSyntaxException e) {
        return createSanitizedErrorResponse(
            request,
            e,
            "JSON syntax error",
            "Invalid request format. Please check your input and try again."
        )
    }

    /**
     * Handle argument conversion errors (includes enum deserialization) with sanitized response
     * This catches errors where request parameters can't be converted to expected types
     */
    @Error(global = true, exception = ConversionErrorException.class)
    static HttpResponse<ErrorResponse> handleConversionError(HttpRequest request, ConversionErrorException e) {
        return createSanitizedErrorResponse(
            request,
            e,
            "Conversion error",
            "Invalid request format. Please check your input and try again."
        )
    }

    /**
     * Handle bean validation errors with sanitized response
     */
    @Error(global = true, exception = ConstraintViolationException.class)
    static HttpResponse<ErrorResponse> handleValidationError(HttpRequest request, ConstraintViolationException e) {
        return createSanitizedErrorResponse(
            request,
            e,
            "Validation error",
            "Invalid input parameters"
        )
    }

    @Error(global = true)
    HttpResponse<JsonError> handleException(HttpRequest request, Throwable exception) {
        handler.handle(request, exception, (String message, String code)-> new JsonError(message))
    }

}
