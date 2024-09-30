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

package io.seqera.wave.service.scan

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.service.job.JobEntry
import io.seqera.wave.store.state.StateEntry

/**
 * Model for scan result
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@ToString(includePackage = false, includeNames = true)
@Canonical
@CompileStatic
class ScanEntry implements StateEntry<String>, JobEntry {

    static final public String PENDING = 'PENDING'
    static final public String SUCCEEDED = 'SUCCEEDED'
    static final public String FAILED = 'FAILED'

    /**
     * The scan unique Id
     */
    final String scanId

    /**
     * The request that original this can. Can be either a container request, build request and mirror request
     */
    final String requestId

    /**
     * The target container image to be scanner
     */
    final String containerImage

    /**
     * The request creation time
     */
    final Instant startTime

    /**
     * How long the scan operation required
     */
    final Duration duration

    /**
     * The status of the scan operation
     */
    final String status

    /**
     * The list of security vulnerabilities reported
     */
    final List<ScanVulnerability> vulnerabilities

    /**
     * The scan job exit status
     */
    final Integer exitCode

    /**
     * The scan job logs
     */
    final String logs

    @Override
    String getKey() {
        return scanId
    }

    @Override
    boolean done() {
        return duration != null
    }

    boolean succeeded() { status==SUCCEEDED }

    @Deprecated
    boolean completed() { done() }

    ScanEntry success(List<ScanVulnerability> vulnerabilities){
        return new ScanEntry(
                this.scanId,
                this.requestId,
                this.containerImage,
                this.startTime,
                Duration.between(this.startTime, Instant.now()),
                SUCCEEDED,
                vulnerabilities,
                0 )
    }

    ScanEntry failure(Integer exitCode, String logs){
        return new ScanEntry(this.scanId, this.requestId, this.containerImage, this.startTime, Duration.between(this.startTime, Instant.now()), FAILED, List.of(), exitCode, logs)
    }

    static ScanEntry failure(ScanRequest request){
        return new ScanEntry(request.scanId, request.requestId, request.targetImage, request.creationTime, Duration.between(request.creationTime, Instant.now()), FAILED, List.of())
    }

    static ScanEntry pending(String scanId, String requestId, String containerImage) {
        return new ScanEntry(scanId, requestId, containerImage, Instant.now(), null, PENDING, List.of())
    }

    static ScanEntry create(String scanId, String requestId, String containerImage, Instant startTime, Duration duration1, String status, List<ScanVulnerability> vulnerabilities){
        return new ScanEntry(scanId, requestId, containerImage, startTime, duration1, status, vulnerabilities)
    }
}
