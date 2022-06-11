package io.seqera.controller

import groovy.util.logging.Slf4j
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.seqera.exchange.ServiceInfo
import io.seqera.exchange.ServiceInfoResponse
import io.seqera.util.BuildInfo

/**
 * Basic service info endpoint
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Controller("/")
class ServiceInfoController {

    @Get('/service-info')
    HttpResponse<ServiceInfoResponse> info() {
        final info = new ServiceInfo()
        info.version = BuildInfo.getVersion()
        info.commitId = BuildInfo.getCommitId()
        HttpResponse.ok(new ServiceInfoResponse(info))
    }

}
