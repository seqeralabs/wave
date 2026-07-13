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
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Produces
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.api.v1.model.BuildStatusResponse
import io.seqera.wave.api.v1.model.WaveBuildRecord
import io.seqera.wave.api.v1.spec.BuildsApiSpec
import io.seqera.wave.controller.v1.mapper.BuildsV1Mapper
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.logs.BuildLogService
import jakarta.inject.Inject

import static io.micronaut.http.HttpStatus.NOT_FOUND

/**
 * Implements the v1 builds API at /w1/builds/*.
 *
 * Four routes are provided:
 *   GET /w1/builds/{id}           – full build record
 *   GET /w1/builds/{id}/status    – build status
 *   GET /w1/builds/{id}/logs      – build log (text/plain)
 *   GET /w1/builds/{id}/condalock – conda lock file (text/plain)
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
class BuildsV1Controller implements BuildsApiSpec {

    @Inject
    private ContainerBuildService buildService

    @Inject
    @Nullable
    private BuildLogService logService

    @Override
    WaveBuildRecord getBuildRecord(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/builds/${id}")
        else
            log.debug("GET /w1/builds/${id}")
        final record = buildService.getBuildRecord(id)
        if( record == null )
            throw new HttpStatusException(NOT_FOUND, "Build record not found: ${id}")
        return BuildsV1Mapper.toV1Record(record)
    }

    @Override
    BuildStatusResponse getBuildStatus(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/builds/${id}/status")
        else
            log.debug("GET /w1/builds/${id}/status")
        final record = buildService.getBuildRecord(id)
        if( record == null )
            throw new HttpStatusException(NOT_FOUND, "Build record not found: ${id}")
        return BuildsV1Mapper.toV1Status(record.toStatusResponse())
    }

    @Override
    @Produces(MediaType.TEXT_PLAIN)
    String getBuildLogs(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/builds/${id}/logs")
        else
            log.debug("GET /w1/builds/${id}/logs")
        if( logService == null )
            throw new IllegalStateException("Build Logs service not configured")
        final stream = logService.fetchLogStream(id)
        if( stream == null )
            throw new HttpStatusException(NOT_FOUND, "Build logs not found: ${id}")
        return streamToString(stream)
    }

    @Override
    @Produces(MediaType.TEXT_PLAIN)
    String getBuildCondaLock(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/builds/${id}/condalock")
        else
            log.debug("GET /w1/builds/${id}/condalock")
        if( logService == null )
            throw new IllegalStateException("Build Logs service not configured")
        final stream = logService.fetchCondaLockStream(id)
        if( stream == null )
            throw new HttpStatusException(NOT_FOUND, "Conda lock file not found: ${id}")
        return streamToString(stream)
    }

    private static String streamToString(StreamedFile file) {
        return file.inputStream.text
    }
}
