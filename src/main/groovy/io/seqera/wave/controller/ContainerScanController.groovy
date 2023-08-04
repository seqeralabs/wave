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
    final ContainerScanService containerScanService

    ContainerScanController(ContainerScanService ContainerScanService) {
        this.containerScanService = ContainerScanService
    }

    @Get("/v1alpha1/scan/{scanId}")
    HttpResponse<WaveScanRecord> scanImage(String scanId){
        final record = containerScanService.getScanResult(scanId)
        return record
                ? HttpResponse.ok(record)
                : HttpResponse.<WaveScanRecord>notFound()
    }
}
