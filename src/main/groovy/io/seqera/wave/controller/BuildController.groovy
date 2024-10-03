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
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.persistence.WaveBuildRecord
import jakarta.inject.Inject
/**
 * Implements a controller for container builds
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/")
@ExecuteOn(TaskExecutors.IO)
class BuildController {

    @Inject
    private ContainerBuildService buildService

    @Inject
    private ContainerMirrorService mirrorService

    @Inject
    @Nullable
    BuildLogService logService

    @Get("/v1alpha1/builds/{buildId}")
    HttpResponse<WaveBuildRecord> getBuildRecord(String buildId) {
        final record = buildService.getBuildRecord(buildId)
        return record
                ? HttpResponse.ok(record)
                : HttpResponse.<WaveBuildRecord>notFound()
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get(value="/v1alpha1/builds/{buildId}/logs")
    HttpResponse<StreamedFile> getBuildLog(String buildId){
        if( logService==null )
            throw new IllegalStateException("Build Logs service not configured")
        final logs = logService.fetchLogStream(buildId)
        return logs
                ? HttpResponse.ok(logs)
                : HttpResponse.<StreamedFile>notFound()
    }

    @Get("/v1alpha1/builds/{buildId}/status")
    HttpResponse<BuildStatusResponse> getBuildStatus(String buildId) {
        final resp = buildResponse0(buildId)
        resp != null
            ? HttpResponse.ok(resp)
            : HttpResponse.<BuildStatusResponse>notFound()
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get(value="/v1alpha1/builds/{buildId}/condalock")
    HttpResponse<StreamedFile> getCondaLock(String buildId){
        if( logService==null )
            throw new IllegalStateException("Build Logs service not configured")
        final condaLock = logService.fetchCondaLock(buildId)
        return condaLock
                ? HttpResponse.ok(condaLock)
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + buildId  + ".lock\"")
                : HttpResponse.<StreamedFile>notFound()
    }

    protected BuildStatusResponse buildResponse0(String buildId) {
        if( !buildId )
            throw new BadRequestException("Missing 'buildId' parameter")
        // build IDs starting with the `mr-` prefix are interpreted as mirror requests
        if( buildId.startsWith(MirrorRequest.ID_PREFIX) ) {
            return mirrorService
                    .getMirrorEntry(buildId)
                    ?.toStatusResponse()
        }
        else {
            return buildService
                    .getBuildRecord(buildId)
                    ?.toStatusResponse()
        }
    }

}
