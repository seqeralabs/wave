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

package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Produces
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.service.access.RecordAccessService
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.logs.BuildLogService
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.persistence.WaveBuildRecord
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.request.ContainerRequestService
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.tower.RecordOwner
import jakarta.inject.Inject

/**
 * The read API that applies the access check. Authentication is optional: a public record is
 * returned without a token, exactly as the current endpoints do.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Controller("/v1alpha3")
@ExecuteOn(TaskExecutors.BLOCKING)
@CompileStatic
class ReadApiController {

    @Inject
    private RecordAccessService accessService

    @Inject
    @Nullable
    private ContainerBuildService buildService

    @Inject
    @Nullable
    private ContainerScanService scanService

    @Inject
    @Nullable
    private ContainerMirrorService mirrorService

    @Inject
    @Nullable
    private ContainerRequestService containerService

    @Inject
    @Nullable
    private BuildLogService logService

    /** @return null when the caller may read the record, otherwise the refusal to send */
    protected HttpResponse<?> refuse(RecordOwner owner, String visibility, String authorization) {
        final decision = accessService.check(owner, visibility, bearer(authorization))
        switch (decision) {
            case RecordAccessService.Decision.ALLOW:
                return null
            case RecordAccessService.Decision.UNAUTHORIZED:
                return HttpResponse.unauthorized()
            case RecordAccessService.Decision.FORBIDDEN:
                return HttpResponse.status(HttpStatus.FORBIDDEN)
            default:
                return HttpResponse.status(HttpStatus.SERVICE_UNAVAILABLE)
        }
    }

    static protected String bearer(String authorization) {
        if( !authorization )
            return null
        final value = authorization.trim()
        return value.toLowerCase().startsWith('bearer ') ? value.substring(7).trim() : null
    }

    @Get("/builds/{buildId}")
    HttpResponse<?> getBuildRecord(String buildId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization) {
        final record = buildService?.getBuildRecord(buildId)
        if( !record )
            return HttpResponse.notFound()
        return refuse(record.owner, record.visibility, authorization) ?: HttpResponse.ok(record)
    }

    @Get("/builds/{buildId}/status")
    HttpResponse<?> getBuildStatus(String buildId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization) {
        final record = buildService?.getBuildRecord(buildId)
        if( !record )
            return HttpResponse.notFound()
        return refuse(record.owner, record.visibility, authorization) ?: HttpResponse.ok(record.toStatusResponse())
    }

    @Get("/builds/{buildId}/logs")
    @Produces(MediaType.TEXT_PLAIN)
    HttpResponse<?> getBuildLogs(String buildId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization) {
        final record = buildService?.getBuildRecord(buildId)
        if( !record )
            return HttpResponse.notFound()
        final denied = refuse(record.owner, record.visibility, authorization)
        if( denied )
            return denied
        final logs = logService?.fetchLogStream(buildId)
        return logs ? HttpResponse.ok(logs) : HttpResponse.notFound()
    }

    @Get("/mirrors/{mirrorId}")
    HttpResponse<?> getMirrorRecord(String mirrorId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization) {
        final record = mirrorService?.getMirrorResult(mirrorId)
        if( !record )
            return HttpResponse.notFound()
        return refuse(record.owner, record.visibility, authorization) ?: HttpResponse.ok(record)
    }

    @Get("/scans/{scanId}")
    HttpResponse<?> getScanRecord(String scanId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization) {
        final record = scanService?.getScanRecord(scanId)
        if( !record )
            return HttpResponse.notFound()
        return refuse(record.owner, record.visibility, authorization) ?: HttpResponse.ok(record)
    }

    @Get("/container/{requestId}")
    HttpResponse<?> getContainerRecord(String requestId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization) {
        final record = containerService?.loadContainerRecord(requestId)
        if( !record )
            return HttpResponse.notFound()
        return refuse(record.owner, record.visibility, authorization) ?: HttpResponse.ok(record.withUserIdOnly())
    }

}
