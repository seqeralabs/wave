/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.controller.v1

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.context.ServerRequestContext
import io.micronaut.http.exceptions.HttpStatusException
import io.micronaut.scheduling.TaskExecutors
import io.micronaut.scheduling.annotation.ExecuteOn
import io.seqera.wave.api.v1.model.ContainerRequest
import io.seqera.wave.api.v1.model.ContainerResponse
import io.seqera.wave.api.v1.model.ContainerStatusResponse
import io.seqera.wave.api.v1.model.WaveContainerRecord
import io.seqera.wave.api.v1.spec.ContainersApiSpec
import io.seqera.wave.controller.ContainerRequestHandler
import io.seqera.wave.controller.v1.mapper.ContainersV1Mapper
import jakarta.inject.Inject

import static io.micronaut.http.HttpStatus.NOT_FOUND

/**
 * Implements the v1 containers API at /w1/containers/*.
 *
 * Four routes are provided:
 *   POST   /w1/containers        – submit a container token request
 *   GET    /w1/containers/{id}   – get full container record
 *   GET    /w1/containers/{id}/status – get container status
 *   DELETE /w1/containers/{id}   – revoke container token
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Controller
@ExecuteOn(TaskExecutors.BLOCKING)
class ContainersV1Controller implements ContainersApiSpec {

    @Inject
    private ContainerRequestHandler handler

    @Override
    ContainerResponse createContainer(ContainerRequest containerRequest) {
        if( log.isTraceEnabled() )
            log.trace("POST /w1/containers")
        else
            log.debug("POST /w1/containers")
        final httpRequest = ServerRequestContext.currentRequest().orElse(null) as HttpRequest
        final internal = ContainersV1Mapper.toInternalRequest(containerRequest)
        final result = handler.submit(internal, httpRequest)
        return ContainersV1Mapper.toV1Response(result)
    }

    @Override
    WaveContainerRecord getContainerDetails(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/containers/${id}")
        else
            log.debug("GET /w1/containers/${id}")
        final record = handler.findRecord(id)
        if( record == null )
            throw new HttpStatusException(NOT_FOUND, "Container record not found: ${id}")
        return ContainersV1Mapper.toV1Record(record)
    }

    @Override
    ContainerStatusResponse getContainerStatus(String id) {
        if( log.isTraceEnabled() )
            log.trace("GET /w1/containers/${id}/status")
        else
            log.debug("GET /w1/containers/${id}/status")
        final status = handler.findStatus(id)
        if( status == null )
            throw new HttpStatusException(NOT_FOUND, "Container status not found: ${id}")
        return ContainersV1Mapper.toV1Status(status)
    }

    @Override
    HttpResponse<Void> deleteContainer(String id) {
        if( log.isTraceEnabled() )
            log.trace("DELETE /w1/containers/${id}")
        else
            log.debug("DELETE /w1/containers/${id}")
        final revoked = handler.revoke(id)
        if( !revoked )
            throw new HttpStatusException(NOT_FOUND, "Container token not found: ${id}")
        return HttpResponse.noContent()
    }

}
