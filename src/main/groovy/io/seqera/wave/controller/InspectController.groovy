package io.seqera.wave.controller

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.exchange.InspectRequest
import io.seqera.wave.exchange.InspectResponse
import io.seqera.wave.service.inspect.ContainerInspectService
import jakarta.inject.Inject
/**
 * Implement container inspect capability
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/")
@ExecuteOn(TaskExecutors.IO)
class InspectController {

    @Inject
    private ContainerInspectService inspectService

    @Post("/v1alpha1/inspect")
    HttpResponse<InspectResponse> scanImage(InspectRequest request){
        final spec = inspectService.containerSpec(request.containerImage, null, null, null, null)
        return spec
                ? HttpResponse.ok(new InspectResponse(spec))
                : HttpResponse.<InspectResponse>notFound()
    }

}
