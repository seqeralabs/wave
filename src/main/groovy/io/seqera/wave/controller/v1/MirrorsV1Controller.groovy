/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller.v1

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Produces
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.api.v1.model.ContainerMirrorResponse
import io.seqera.wave.api.v1.spec.MirrorsApiSpec
import io.seqera.wave.controller.v1.mapper.MirrorsV1Mapper
import io.seqera.wave.exception.UnsupportedMirrorServiceException
import io.seqera.wave.service.mirror.ContainerMirrorService
import jakarta.inject.Inject

import static io.micronaut.http.HttpStatus.NOT_FOUND

/**
 * Implements the v1 mirrors API at /w1/mirrors/*.
 *
 * Two routes are provided:
 *   GET /w1/mirrors/{id}       – full mirror record as JSON
 *   GET /w1/mirrors/{id}/logs  – mirror log as octet-stream attachment
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
class MirrorsV1Controller implements MirrorsApiSpec {

    @Inject
    @Nullable
    private ContainerMirrorService mirrorService

    @Override
    ContainerMirrorResponse containerMirror(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/mirrors/${id}")
        else
            log.debug("GET /w1/mirrors/${id}")
        if( !mirrorService )
            throw new UnsupportedMirrorServiceException()
        final result = mirrorService.getMirrorResult(id)
        if( result == null )
            throw new HttpStatusException(NOT_FOUND, "Mirror record not found: ${id}")
        return MirrorsV1Mapper.toV1(result)
    }

    @Override
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    HttpResponse<String> getMirrorLogs(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/mirrors/${id}/logs")
        else
            log.debug("GET /w1/mirrors/${id}/logs")
        if( !mirrorService )
            throw new UnsupportedMirrorServiceException()
        final result = mirrorService.getMirrorResult(id)
        if( result == null )
            throw new HttpStatusException(NOT_FOUND, "Mirror record not found: ${id}")
        final logs = result.logs
        if( logs == null )
            throw new HttpStatusException(NOT_FOUND, "Mirror logs not found: ${id}")
        return HttpResponse.ok(logs)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${id}.log")
    }
}
