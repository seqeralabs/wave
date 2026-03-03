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

import io.seqera.wave.exception.UnsupportedBuildServiceException
import io.seqera.wave.exception.UnsupportedMirrorServiceException
import io.seqera.wave.exception.UnsupportedScanServiceException
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
import io.seqera.wave.service.scan.ScanIds
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
    @Nullable
    private ContainerBuildService buildService

    @Inject
    @Nullable
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
        if( !scanService )
            throw new UnsupportedScanServiceException()
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
        else if( !request.scanId || !state.succeeded ) {
            return createResponse0(DONE, request, state, buildResult(request,state))
        }

        if( request.scanId && request.scanMode == ScanMode.required && scanService ) {
            if( ScanIds.isMulti(request.scanId) ) {
                return handleMultiScanStatus(request, state)
            }
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
            if( !mirrorService )
                throw new UnsupportedMirrorServiceException()
            final mirror = mirrorService.getMirrorResult(request.buildId)
            if (!mirror)
                throw new NotFoundException("Missing container mirror record with id: ${request.buildId}")
            return ContainerState.from(mirror)
        }
        if( request.buildId ) {
            if( !buildService ) throw new UnsupportedBuildServiceException()
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

    protected ContainerStatusResponse handleMultiScanStatus(ContainerRequest request, ContainerState state) {
        final entries = ScanIds.decode(request.scanId)
        final List<ScanEntry> scans = new ArrayList<>(entries.size())
        boolean allDone = true
        boolean allSucceeded = true
        Duration maxScanDuration = Duration.ZERO
        for( Map.Entry<String,String> pair : entries ) {
            final scan = getScanState(pair.key)
            if( !scan )
                throw new NotFoundException("Missing container scan record with id: ${pair.key}")
            scans.add(scan)
            if( !scan.duration ) {
                allDone = false
            }
            else {
                if( scan.duration > maxScanDuration )
                    maxScanDuration = scan.duration
                if( !scan.succeeded() )
                    allSucceeded = false
            }
        }

        if( !allDone ) {
            return createResponse0(SCANNING, request, new ContainerState(state.startTime))
        }

        // all scans completed — pick the first failure or the last success for the response
        final combinedScan = allSucceeded
                ? scans.last()
                : scans.find { !it.succeeded() }
        // use max duration since per-arch scans run in parallel
        final newState = state
                ? new ContainerState(state.startTime, state.duration + maxScanDuration, allSucceeded)
                : new ContainerState(combinedScan.startTime, maxScanDuration, allSucceeded)
        return createScanResponse(request, newState, combinedScan)
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
        final scanDetailId = scan.scanId
        // scan was not successful
        if (!scan.succeeded()) {
            return new StageResult(
                    false,
                    "Container security scan did not complete successfully",
                    "${serverUrl}/view/scans/${scanDetailId}"
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
                    "${serverUrl}/view/scans/${scanDetailId}"
            )
        }

        if( scan.summary() ) {
            return new StageResult(
                    true,
                    "Container security scan operation found one or more vulnerabilities that are compatible with requested security levels: ${allowedLevels.join(',')}",
                    "${serverUrl}/view/scans/${scanDetailId}"
            )
        }

        // all fine
        return new StageResult(true)
    }

}
