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
import io.micronaut.http.HttpHeaders
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.server.types.files.StreamedFile
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.persistence.WaveScanRecord
import io.seqera.wave.service.scan.ScanType
import io.seqera.wave.service.scan.plugin.PluginScanResponse
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

    @Get("/v1alpha1/scans/{scanId}")
    HttpResponse<WaveScanRecord> scanImage(String scanId){
        final record = scanService.getScanRecord(scanId)
        return record
                ? HttpResponse.ok(record)
                : HttpResponse.<WaveScanRecord>notFound()
    }

    @Produces(MediaType.TEXT_PLAIN)
    @Get(value="/v1alpha1/scans/{scanId}/spdx")
    HttpResponse<StreamedFile> getSbomSPDX(String scanId){
        final report = scanService.fetchReportStream(scanId, ScanType.Spdx)
        return report
                ? HttpResponse.ok(report).header("Content-Disposition", "attachment; filename=\"spdx-${scanId}.json\"")
                : HttpResponse.<StreamedFile>notFound()
    }

    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Get("/v1alpha1/scans/{scanId}/logs")
    HttpResponse<String> getScanLog(String scanId){
        final logs = scanService.getScanRecord(scanId).logs
        return logs
                ? (HttpResponse.ok(logs)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=${scanId}.log"))
                : HttpResponse.<String>notFound()
    }

    @Produces(MediaType.APPLICATION_JSON)
    @Get("/v1alpha1/scans/plugins")
    HttpResponse<PluginScanResponse> scanPlugin(@QueryValue String plugin){
        final res = scanService.scanPlugin(plugin)
        return res ? HttpResponse.ok(res) : HttpResponse.<PluginScanResponse>notFound()
    }

}
