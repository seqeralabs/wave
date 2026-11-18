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
import io.micronaut.context.annotation.Requires
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
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.view.auth.ViewAuthService
import io.seqera.wave.service.scan.ScanType
import jakarta.inject.Inject

/**
 * Implements a controller to receive get scan result of build images
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
@Requires(bean = ContainerScanService)
@Controller("/")
@ExecuteOn(TaskExecutors.BLOCKING)
class ScanController {
    
    @Inject
    private ContainerScanService scanService

    @Inject
    private RecordAccessGuard guard

    @Get("/v1alpha1/scans/{scanId}")
    HttpResponse<?> scanImage(String scanId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization, @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId){
        final record = scanService.getScanRecord(scanId)
        if( !record )
            return HttpResponse.notFound()
        return guard.refuse(record.owner, record.visibility, authorization, sessionId) ?: HttpResponse.ok(record)
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get(value="/v1alpha1/scans/{scanId}/spdx")
    HttpResponse<?> getSbomSPDX(String scanId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization, @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId){
        final denied = refuseScan(scanId, authorization, sessionId)
        if( denied )
            return denied
        final report = scanService.fetchReportStream(scanId, ScanType.Spdx)
        return report
                ? HttpResponse.ok(report).header("Content-Disposition", "attachment; filename=\"spdx-${scanId}.json\"")
                : HttpResponse.notFound()
    }

    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Get("/v1alpha1/scans/{scanId}/logs")
    HttpResponse<?> getScanLog(String scanId, @Nullable @Header(HttpHeaders.AUTHORIZATION) String authorization, @Nullable @CookieValue(ViewAuthService.COOKIE) String sessionId){
        final record = scanService.getScanRecord(scanId)
        if( !record )
            return HttpResponse.notFound()
        final denied = guard.refuse(record.owner, record.visibility, authorization, sessionId)
        if( denied )
            return denied
        return record.logs
                ? (HttpResponse.ok(record.logs)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${scanId}.log"))
                : HttpResponse.notFound()
    }

    /** the report routes have no record of their own: they are guarded by the scan that owns them */
    private HttpResponse<?> refuseScan(String scanId, String authorization, String sessionId) {
        // a record that has aged out leaves the report unguarded, as it was before: refusing
        // here would 404 a route released clients can still read today
        final record = scanService.getScanRecord(scanId)
        return record ? guard.refuse(record.owner, record.visibility, authorization, sessionId) : null
    }

}
