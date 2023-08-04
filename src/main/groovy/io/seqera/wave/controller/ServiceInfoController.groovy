package io.seqera.wave.controller

import javax.annotation.Nullable

import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
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
@ExecuteOn(TaskExecutors.IO)
class ServiceInfoController {

    @Value('${wave.landing.url}')
    @Nullable
    String landingUrl

    @Get('/service-info')
    HttpResponse<ServiceInfoResponse> info() {
        final info = new ServiceInfo(BuildInfo.getVersion(), BuildInfo.getCommitId())
        HttpResponse.ok(new ServiceInfoResponse(info))
    }

    @Get("/")
    HttpResponse landing() {
        return landingUrl
                ? HttpResponse.redirect(new URI(landingUrl))
                : HttpResponse.badRequest()
    }

}
