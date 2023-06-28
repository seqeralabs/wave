package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.seqera.wave.service.ContainerScanService

@Slf4j
@CompileStatic
@Controller("/")
class ContainerScanController {
    final ContainerScanService containerScanService

    ContainerScanController(ContainerScanService ContainerScanService) {
        this.containerScanService = ContainerScanService
    }

    @Get("/scan/{buildId}")
    HttpResponse<String> scanImage(String buildId){
        HttpResponse.ok(containerScanService.getScanResult(buildId))
    }
}
