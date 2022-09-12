package io.seqera.wave.controller

import groovy.util.logging.Slf4j
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.seqera.wave.api.ServiceInfo
import io.seqera.wave.api.ServiceInfoResponse
import io.seqera.wave.util.BuildInfo
import io.swagger.annotations.ApiResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema

/**
 * Basic service info endpoint
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Controller("/")
class ServiceInfoController {

    @Get('/service-info')
    @Operation(summary = "Info about the service",
            operationId = "service-info",
            description = "Endpoint to know the running version and last commit in repo")
    @ApiResponse(code = 200, response = ServiceInfoResponse)
    HttpResponse<ServiceInfoResponse> info() {
        final info = new ServiceInfo()
        info.version = BuildInfo.getVersion()
        info.commitId = BuildInfo.getCommitId()
        HttpResponse.ok(new ServiceInfoResponse(info))
    }

}
