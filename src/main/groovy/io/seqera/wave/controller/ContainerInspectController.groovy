package io.seqera.wave.controller


import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.api.ContainerInspectRequest
import io.seqera.wave.api.ContainerInspectResponse
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.UserService
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.service.pairing.PairingService
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
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
class ContainerInspectController {

    @Inject
    private ContainerInspectService inspectService

    @Inject
    private PairingService pairingService

    @Inject
    private UserService userService

    @Inject
    @Value('${tower.endpoint.url:`https://api.tower.nf`}')
    private String towerEndpointUrl

    @Inject
    @Value('${wave.server.url}')
    private String serverUrl

    @Post("/v1alpha1/inspect")
    CompletableFuture<HttpResponse<ContainerInspectResponse>> inspect(ContainerInspectRequest req) {

        // this is needed for backward compatibility with old clients
        if( !req.towerEndpoint ) {
            req.towerEndpoint = towerEndpointUrl
        }

        // anonymous access
        if( !req.towerAccessToken ) {
            return CompletableFuture.completedFuture(makeResponse(req, PlatformId.NULL))
        }

        // We first check if the service is registered
        final registration = pairingService.getPairingRecord(PairingService.TOWER_SERVICE, req.towerEndpoint)
        if( !registration )
            throw new BadRequestException("Tower instance '${req.towerEndpoint}' has not enabled to connect Wave service '$serverUrl'")

        // find out the user associated with the specified tower access token
        return userService
                .getUserByAccessTokenAsync(registration.endpoint, req.towerAccessToken)
                .thenApply { User user -> makeResponse(req, PlatformId.of(user,req)) }

    }

    protected HttpResponse<ContainerInspectResponse> makeResponse(ContainerInspectRequest req, PlatformId identity) {
        final spec = inspectService.containerSpec(req.containerImage, identity)
        return spec
                ? HttpResponse.ok(new ContainerInspectResponse(spec))
                : HttpResponse.<ContainerInspectResponse>notFound()
    }
}
