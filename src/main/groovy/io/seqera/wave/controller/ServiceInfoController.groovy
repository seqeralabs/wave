package io.seqera.wave.controller

import groovy.util.logging.Slf4j
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.security.annotation.Secured
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.api.ServiceInfo
import io.seqera.wave.api.ServiceInfoResponse
import io.seqera.wave.util.BuildInfo

/**
 * Basic service info endpoint
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Controller("/")
@Secured(SecurityRule.IS_ANONYMOUS)
class ServiceInfoController {

    @Get('/service-info')
    HttpResponse<ServiceInfoResponse> info() {
        final info = new ServiceInfo()
        info.version = BuildInfo.getVersion()
        info.commitId = BuildInfo.getCommitId()
        HttpResponse.ok(new ServiceInfoResponse(info))
    }

}
