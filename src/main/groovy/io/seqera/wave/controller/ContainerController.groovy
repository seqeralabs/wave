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

import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Delete
import io.micronaut.http.annotation.Error
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Post
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.micronaut.security.annotation.Secured
import io.micronaut.security.authentication.AuthorizationException
import io.micronaut.security.rules.SecurityRule
import io.seqera.wave.api.ContainerStatusResponse
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.api.SubmitContainerTokenResponse
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.exchange.DescribeWaveContainerResponse
import io.seqera.wave.service.persistence.WaveContainerRecord
import static io.micronaut.http.HttpHeaders.WWW_AUTHENTICATE

/**
 * Implement a controller to receive container token requests
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller("/")
@ExecuteOn(TaskExecutors.BLOCKING)
class ContainerController extends ContainerRequestHandler {

    @PostConstruct
    private void init() {
        log.info "Wave server url: $serverUrl; allowAnonymous: $allowAnonymous; tower-endpoint-url: $towerEndpointUrl; default-build-repo: ${buildConfig?.defaultBuildRepository}; default-cache-repo: ${buildConfig?.defaultCacheRepository}; default-public-repo: ${buildConfig?.defaultPublicRepository}"
    }

    @Deprecated
    @Post('/container-token')
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<SubmitContainerTokenResponse> getToken(HttpRequest httpRequest, @Body SubmitContainerTokenRequest req) {
        return getContainerImpl(httpRequest, req, false)
    }

    @Post('/v1alpha2/container')
    @ExecuteOn(TaskExecutors.BLOCKING)
    HttpResponse<SubmitContainerTokenResponse> getTokenV2(HttpRequest httpRequest, @Body SubmitContainerTokenRequest req) {
        return getContainerImpl(httpRequest, req, true)
    }

    @Get('/container-token/{token}')
    HttpResponse<DescribeWaveContainerResponse> describeContainerRequest(String token) {
        final data = containerService.loadContainerRecord(token)
        if( !data )
            throw new NotFoundException("Missing container record for token: $token")
        // return the response
        return HttpResponse.ok( DescribeWaveContainerResponse.create(token, data) )
    }

    @Secured(SecurityRule.IS_AUTHENTICATED)
    @Delete('/container-token/{token}')
    HttpResponse deleteContainerRequest(String token) {
        final record = containerService.evictRequest(token)
        if( !record ){
            throw new NotFoundException("Missing container record for token: $token")
        }
        return HttpResponse.ok()
    }

    @Error(exception = AuthorizationException.class)
    HttpResponse<?> handleAuthorizationException() {
        return HttpResponse.unauthorized()
                .header(WWW_AUTHENTICATE, "Basic realm=Wave Authentication")
    }

    @Get('/v1alpha2/container/{requestId}')
    HttpResponse<WaveContainerRecord> getContainerDetails(String requestId) {
        final rec = findRecord(requestId)
        if( !rec )
            return HttpResponse.notFound()
        return HttpResponse.ok(rec)
    }

    @Get('/v1alpha2/container/{requestId}/status')
    HttpResponse<ContainerStatusResponse> getContainerStatus(String requestId) {
        final ContainerStatusResponse resp = findStatus(requestId)
        if( !resp )
            return HttpResponse.notFound()
        return HttpResponse.ok(resp)
    }

}
