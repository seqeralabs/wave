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

package io.seqera.wave.service.request

import static io.seqera.wave.api.ContainerStatus.*

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.api.ContainerStatus
import io.seqera.wave.api.ContainerStatusResponse
import io.seqera.wave.api.ScanMode
import io.seqera.wave.exception.NotFoundException
import io.seqera.wave.service.builder.ContainerBuildService
import io.seqera.wave.service.mirror.ContainerMirrorService
import io.seqera.wave.service.scan.ContainerScanService
import io.seqera.wave.service.scan.ScanEntry
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements the {@link ContainerStatusService} service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerStatusServiceImpl implements ContainerStatusService {

    @Canonical
    static class StageResult {
        boolean succeeded
        String reason
        String detailsUri
    }

    @Inject
    private ContainerBuildService buildService

    @Inject
    private ContainerMirrorService mirrorService

    @Inject
    @Nullable
    private ContainerScanService scanService

    @Inject
    private ContainerRequestStore requestStore

    @Inject
    @Value('${wave.server.url}')
    private String serverUrl

    protected ScanEntry getScanState(String scanId) {
        final entry = scanService.getScanState(scanId)
        if( entry!=null )
            return entry
        final recrd = scanService.getScanRecord(scanId)
        return recrd!=null ? ScanEntry.of(recrd) : null
    }

    @Override
    ContainerStatusResponse getContainerStatus(String requestId) {

        final request = requestStore.get(requestId)
        if( !request )
            return null

        final ContainerState state = getContainerState(request)

        if( state.running ) {
            return createResponse0(BUILDING, request, state)
        }
        else if( !request.scanId ) {
            return createResponse0(DONE, request, state, buildResult(request,state))
        }

        if( request.scanId && request.scanMode == ScanMode.required && scanService ) {
            final scan = getScanState(request.scanId)
            if ( !scan )
                throw new NotFoundException("Missing container scan record with id: ${request.scanId}")
            if ( !scan.duration ) {
                return createResponse0(SCANNING, request, new ContainerState(state.startTime))
            }
            else {
                final newState = state
                        ? new ContainerState(state.startTime, state.duration+scan.duration, scan.succeeded())
                        : new ContainerState(scan.startTime, scan.duration, scan.succeeded())
                return createScanResponse(request, newState, scan)
            }
        }

        return createResponse0(DONE, request, state)
    }

    protected ContainerState getContainerState(ContainerRequest request) {
        if( request.mirror && request.buildId ) {
            final mirror = mirrorService.getMirrorResult(request.buildId)
            if (!mirror)
                throw new NotFoundException("Missing container mirror record with id: ${request.buildId}")
            return ContainerState.from(mirror)
        }
        if( request.buildId ) {
            final build = buildService.getBuildRecord(request.buildId)
            if (!build)
                throw new NotFoundException("Missing container build record with id: ${request.buildId}")
            return ContainerState.from(build)
        }
        else {
            final delta = Duration.between(request.creationTime, Instant.now())
            return new ContainerState( request.creationTime, delta,true )
        }
    }

    protected ContainerStatusResponse createResponse0(ContainerStatus status, ContainerRequest request, ContainerState state, StageResult result=null) {
        new ContainerStatusResponse(
                request.requestId,
                status,
                !request.mirror ? request.buildId : null,
                request.mirror ? request.buildId : null,
                request.scanId,
                null,
                state.succeeded,
                result?.reason,
                result?.detailsUri,
                state.startTime,
                state.duration,
        )
    }

    protected ContainerStatusResponse createScanResponse(ContainerRequest request, ContainerState state, ScanEntry scan) {

        final result = scanResult(request, scan)
        return new ContainerStatusResponse(
                request.requestId,
                DONE,
                !request.mirror ? request.buildId : null,
                request.mirror ? request.buildId : null,
                request.scanId,
                scan.summary(),
                result.succeeded,
                result.reason,
                result.detailsUri,
                state.startTime,
                state.duration,
        )
    }

    protected StageResult buildResult(ContainerRequest request, ContainerState state) {
        if( state.succeeded )
              return new StageResult(true)

        if( request.mirror ) {
            // when 'mirror' flag is true, then it should be interpreted as a mirror operation
            return new StageResult(false,
                    "Container mirror did not complete successfully",
                    "${serverUrl}/view/mirrors/${request.buildId}" )
        }
        else {
            // plain build
            return new StageResult(false,
                    "Container build did not complete successfully",
                    "${serverUrl}/view/builds/${request.buildId}" )
        }
    }

    protected StageResult scanResult(ContainerRequest request, ScanEntry scan) {
        // scan was not successful
        if (!scan.succeeded()) {
            return new StageResult(
                    false,
                    "Container security scan did not complete successfully",
                    "${serverUrl}/view/scans/${request.scanId}"
            )
        }

        // scan job was successful, check the required levels are matched
        final allowedLevels = request
                .scanLevels
                ?.collect(it -> it.toString().toUpperCase())
                ?: List.of()
        final foundLevels = new HashSet(scan
                .summary()
                ?.keySet()
                ?: Set.of())
        foundLevels.removeAll(allowedLevels)
        if (foundLevels) {
            return new StageResult(
                    false,
                    "Container security scan operation found one or more vulnerabilities with severity: ${foundLevels.join(',')}",
                    "${serverUrl}/view/scans/${request.scanId}"
            )
        }

        if( scan.summary() ) {
            return new StageResult(
                    true,
                    "Container security scan operation found one or more vulnerabilities that are compatible with requested security levels: ${allowedLevels.join(',')}",
                    "${serverUrl}/view/scans/${request.scanId}"
            )
        }

        // all fine
        return new StageResult(true)
    }

}
