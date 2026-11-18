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
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.CookieValue
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.exception.UnsupportedMirrorServiceException
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.view.auth.ViewAuthService
import jakarta.inject.Inject
/**
 * Implements a controller for container mirror apis
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/")
@ExecuteOn(TaskExecutors.BLOCKING)
class MirrorController {

    @Inject
    @Nullable
    private ContainerMirrorService mirrorService

    @Inject
    private RecordAccessGuard guard

    @Get("/v1alpha1/mirrors/{mirrorId}")
    HttpResponse<?> getMirrorRecord(String mirrorId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization, @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId) {
        if( !mirrorService )
            throw new UnsupportedMirrorServiceException()
        final result = mirrorService.getMirrorResult(mirrorId)
        if( !result )
            return HttpResponse.notFound()
        return guard.refuse(result.owner, result.visibility, authorization, sessionId) ?: HttpResponse.ok(result)
    }

    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Get("/v1alpha1/mirrors/{mirrorId}/logs")
    HttpResponse<?> getScanLog(String mirrorId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization, @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId){
        final result = mirrorService?.getMirrorResult(mirrorId)
        if( !result )
            return HttpResponse.notFound()
        final denied = guard.refuse(result.owner, result.visibility, authorization, sessionId)
        if( denied )
            return denied
        return result.logs
                ? (HttpResponse.ok(result.logs)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${mirrorId}.log"))
                : HttpResponse.notFound()
    }

}
