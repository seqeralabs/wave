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

import java.time.Instant

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
import io.seqera.wave.api.v1.model.ScanSubmitRequest
import io.seqera.wave.api.v1.model.ScanSubmitResponse
import io.seqera.wave.api.v1.model.WaveScanRecord
import io.seqera.wave.api.v1.spec.ScansApiSpec
import io.seqera.wave.controller.v1.mapper.ScansV1Mapper
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.scan.ScanId
import io.seqera.wave.service.scan.ScanRequest
import io.seqera.wave.service.scan.ScanType
import jakarta.inject.Inject

import static io.micronaut.http.HttpStatus.BAD_REQUEST
import static io.micronaut.http.HttpStatus.NOT_FOUND

/**
 * Implements the v1 scans API at /w1/scans/*.
 *
 * Four routes are provided:
 *   POST /w1/scans             – submit a new scan request
 *   GET  /w1/scans/{id}        – full scan record as JSON
 *   GET  /w1/scans/{id}/logs   – scan log as octet-stream attachment
 *   GET  /w1/scans/{id}/spdx   – SPDX SBOM document as text/plain attachment
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
class ScansV1Controller implements ScansApiSpec {

    @Inject
    @Nullable
    private ContainerScanService scanService

    @Override
    ScanSubmitResponse submitScan(ScanSubmitRequest req) {
        if( log.isTraceEnabled() )
            log.trace("POST /w1/scans - request=${req}")
        else
            log.debug("POST /w1/scans")
        if( !scanService )
            throw new HttpStatusException(BAD_REQUEST, "Scan service is not available")
        // require exactly one target specification
        final targets = [req.containerImage, req.mirrorId, req.buildId].count { it }
        if( targets == 0 )
            throw new HttpStatusException(BAD_REQUEST, "One of containerImage, buildId or mirrorId must be provided")
        if( targets > 1 )
            throw new HttpStatusException(BAD_REQUEST, "Only one of containerImage, buildId or mirrorId may be provided")
        // resolve target image and associated IDs
        final targetImage = req.containerImage ?: req.buildId ?: req.mirrorId
        final platform = req.containerPlatform ? ContainerPlatform.of(req.containerPlatform) : null
        final scanId = ScanId.of(targetImage).toString()
        final workDir = null as java.nio.file.Path   // no workspace available at API level; impl handles null
        final scanRequest = new ScanRequest(
                scanId,
                req.buildId,
                req.mirrorId,
                null,          // requestId – no container request context
                null,          // configJson – credentials handled internally
                targetImage,
                platform,
                workDir,
                Instant.now(),
                null           // PlatformId / Tower identity
        )
        final record = scanService.submitScan(scanRequest)
        if( !record )
            throw new HttpStatusException(BAD_REQUEST, "Unable to submit scan for target: ${targetImage}")
        return ScansV1Mapper.toV1Submit(record)
    }

    @Override
    WaveScanRecord scanImage(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/scans/${id}")
        else
            log.debug("GET /w1/scans/${id}")
        if( !scanService )
            throw new HttpStatusException(NOT_FOUND, "Scan service is not available")
        final record = scanService.getScanRecord(id)
        if( record == null )
            throw new HttpStatusException(NOT_FOUND, "Scan record not found: ${id}")
        return ScansV1Mapper.toV1Record(record)
    }

    @Override
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    HttpResponse<String> getScanLogs(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/scans/${id}/logs")
        else
            log.debug("GET /w1/scans/${id}/logs")
        if( !scanService )
            throw new HttpStatusException(NOT_FOUND, "Scan service is not available")
        final record = scanService.getScanRecord(id)
        if( record == null )
            throw new HttpStatusException(NOT_FOUND, "Scan record not found: ${id}")
        final logs = record.logs
        if( !logs )
            throw new HttpStatusException(NOT_FOUND, "Scan logs not found: ${id}")
        return HttpResponse.ok(logs)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${id}.log")
    }

    @Override
    @Produces(MediaType.TEXT_PLAIN)
    String getScanSpdx(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/scans/${id}/spdx")
        else
            log.debug("GET /w1/scans/${id}/spdx")
        if( !scanService )
            throw new HttpStatusException(NOT_FOUND, "Scan service is not available")
        final report = scanService.fetchReportStream(id, ScanType.Spdx)
        if( report == null )
            throw new HttpStatusException(NOT_FOUND, "SPDX report not found: ${id}")
        return report.inputStream.text
    }
}
