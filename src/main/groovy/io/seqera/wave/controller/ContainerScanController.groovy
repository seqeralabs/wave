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
import io.micronaut.context.annotation.Requires
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.persistence.WaveScanRecord
import jakarta.inject.Inject

/**
 * Implements a controller to receive get scan result of build images
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@CompileStatic
@Requires(property = 'wave.scan.enabled', value = 'true')
@Controller("/")
@ExecuteOn(TaskExecutors.IO)
class ContainerScanController {
    
    @Inject
    private ContainerScanService containerScanService


    @Get("/v1alpha1/scans/{scanId}")
    HttpResponse<WaveScanRecord> scanImage(String scanId){
        final record = containerScanService.getScanResult(scanId)
        return record
                ? HttpResponse.ok(record)
                : HttpResponse.<WaveScanRecord>notFound()
    }
}
