/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
