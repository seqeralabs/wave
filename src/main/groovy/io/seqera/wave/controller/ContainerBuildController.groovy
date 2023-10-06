/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.persistence.PersistenceService
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
class ContainerBuildController {

    @Inject
    private PersistenceService persistenceService

    @Inject
    BuildLogService logService

    @Get("/v1alpha1/builds/{buildId}")
    HttpResponse<WaveBuildRecord> getBuildRecord(String buildId){
        final record = persistenceService.loadBuild(buildId)
        return record
                ? HttpResponse.ok(record)
                : HttpResponse.<WaveBuildRecord>notFound()
    }

    // TODO consider adding response mimetype
    @Get(value="/v1alpha1/builds/{buildId}/logs")
    HttpResponse<StreamedFile> getBuildLog(String buildId){
        final logs = logService.fetchLogStream(buildId)
        return logs
                ? HttpResponse.ok(logs)
                .contentType(MediaType.APPLICATION_OCTET_STREAM_TYPE)
                : HttpResponse.<StreamedFile>notFound()
    }

}
