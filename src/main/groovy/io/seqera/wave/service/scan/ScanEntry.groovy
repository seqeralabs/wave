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
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
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
    String scanId

    /**
     * The build request that original this scan entry
     */
    String buildId

    /**
     * The container mirror request that original this scan entry
     */
    String mirrorId

    /**
     * The container request that original this scan entry
     */
    String requestId

    /**
     * The target container image to be scanner
     */
    String containerImage

    /**
     * The request creation time
     */
    Instant startTime

    /**
     * How long the scan operation required
     */
    Duration duration

    /**
     * The status of the scan operation
     */
    String status

    /**
     * The list of security vulnerabilities reported
     */
    List<ScanVulnerability> vulnerabilities

    /**
     * The scan job exit status
     */
    Integer exitCode

    /**
     * The scan job logs
     */
    String logs

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

    static ScanEntry create(ScanRequest request) {
        return new ScanEntry(
                request.scanId,
                request.buildId,
                request.mirrorId,
                request.requestId,
                request.targetImage,
                request.creationTime,
                null,
                PENDING,
                List.of())
    }

    ScanEntry success(List<ScanVulnerability> vulnerabilities){
        return new ScanEntry(
                this.scanId,
                this.buildId,
                this.mirrorId,
                this.requestId,
                this.containerImage,
                this.startTime,
                Duration.between(this.startTime, Instant.now()),
                SUCCEEDED,
                vulnerabilities,
                0 )
    }

    ScanEntry failure(Integer exitCode, String logs){
        return new ScanEntry(
                this.scanId,
                this.buildId,
                this.mirrorId,
                this.requestId,
                this.containerImage,
                this.startTime,
                Duration.between(this.startTime, Instant.now()),
                FAILED,
                List.of(),
                exitCode,
                logs)
    }

    static ScanEntry failure(ScanRequest request){
        return new ScanEntry(
                request.scanId,
                request.buildId,
                request.mirrorId,
                request.requestId,
                request.targetImage,
                request.creationTime,
                Duration.between(request.creationTime, Instant.now()),
                FAILED,
                List.of())
    }


    Map<String,Integer> summary() {
        final result = new HashMap<String,Integer>()
        if( !vulnerabilities )
            return result
        for( ScanVulnerability it : vulnerabilities ) {
            def v = result.getOrDefault(it.severity, 0)
            result.put(it.severity, v+1)
        }
        return result
    }

    static ScanEntry of(Map opts){
        return new ScanEntry(
                opts.scanId as String,
                opts.buildId as String,
                opts.mirrorId as String,
                opts.requestId as String,
                opts.containerImage as String,
                opts.startTime as Instant,
                opts.duration as Duration,
                opts.status as String,
                opts.vulnerabilities as List<ScanVulnerability>,
                opts.exitCode as Integer,
                opts.logs as String
        )
    }

}
