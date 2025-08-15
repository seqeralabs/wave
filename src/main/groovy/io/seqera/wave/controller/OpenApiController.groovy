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
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn

/**
 * OpenAPI endpoints controller
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Controller("/openapi")
@CompileStatic
@ExecuteOn(TaskExecutors.BLOCKING)
class OpenApiController {

    @Get("/")
    HttpResponse getOpenAPI() {
        HttpResponse.redirect(URI.create("/openapi/index.html"))
    }

    @Get("/index.html")
    HttpResponse getOpenAPIUI() {
        final inputStream = getClass().getResourceAsStream("/public/openapi/index.html");
        return inputStream != null ? HttpResponse.ok(inputStream).contentType(MediaType.TEXT_HTML) : HttpResponse.notFound();
    }

    @Get("/{filename}")
    HttpResponse getOpenAPIFile(String filename) {
        final inputStream = getClass().getResourceAsStream("/public/openapi/" + filename);
        if (inputStream != null) {
            final mediaType = filename.endsWith('.yaml') || filename.endsWith('.yml') 
                ? MediaType.of("application/x-yaml")
                : MediaType.TEXT_PLAIN
            return HttpResponse.ok(inputStream).contentType(mediaType);
        }
        return HttpResponse.notFound();
    }
}