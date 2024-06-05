/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
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
import io.seqera.wave.tower.auth.JwtAuth
import jakarta.inject.Inject
import static io.seqera.wave.util.ContainerHelper.patchPlatformEndpoint
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

    @Inject
    private PairingService pairingService

    @Inject
    private UserService userService

    @Inject
    @Value('${tower.endpoint.url:`https://api.cloud.seqera.io`}')
    private String towerEndpointUrl

    @Inject
    @Value('${wave.server.url}')
    private String serverUrl

    @Post("/v1alpha1/inspect")
    CompletableFuture<HttpResponse<ContainerInspectResponse>> inspect(@Body ContainerInspectRequest req) {

        if( !req.containerImage )
            throw new BadRequestException("Missing 'containerImage' attribute")

        // this is needed for backward compatibility with old clients
        if( !req.towerEndpoint ) {
            req.towerEndpoint = towerEndpointUrl
        }
        else {
            req.towerEndpoint = patchPlatformEndpoint(req.towerEndpoint)
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
                .getUserByAccessTokenAsync(registration.endpoint, JwtAuth.of(req))
                .thenApply((User user) -> makeResponse(req, PlatformId.of(user,req)) )

    }

    protected HttpResponse<ContainerInspectResponse> makeResponse(ContainerInspectRequest req, PlatformId identity) {
        final spec = inspectService.containerSpec(req.containerImage, identity)
        return spec
                ? HttpResponse.ok(new ContainerInspectResponse(spec))
                : HttpResponse.<ContainerInspectResponse>notFound()
    }
}
