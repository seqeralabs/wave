/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

/**
 * Model for scan result
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

import groovy.transform.ToString

@ToString(includePackage = false, includeNames = true)
@Canonical
@CompileStatic
class ScanResult {

    static final public String SUCCEEDED = 'SUCCEEDED'
    static final public String FAILED = 'FAILED'

    String id
    String buildId
    Instant startTime
    Duration duration
    String status
    List<ScanVulnerability> vulnerabilities

    private ScanResult(String id, String buildId, Instant startTime, Duration duration, String status, List<ScanVulnerability> vulnerabilities) {
        this.id = id
        this.buildId = buildId
        this.startTime = startTime
        this.duration = duration
        this.status = status
        this.vulnerabilities = vulnerabilities
    }

    boolean isSucceeded() { status==SUCCEEDED }

    boolean isCompleted() { duration!=null }

    static ScanResult success(ScanRequest request, Instant startTime, List<ScanVulnerability> vulnerabilities){
        return new ScanResult(request.id, request.buildId, startTime, Duration.between(startTime, Instant.now()), SUCCEEDED, vulnerabilities)
    }

    static ScanResult failure(ScanRequest request, Instant startTime){
        return new ScanResult(request.id, request.buildId, startTime, Duration.between(startTime, Instant.now()), FAILED, List.of())
    }

    static ScanResult create(String scanId, String buildId, Instant startTime, Duration duration1, String status, List<ScanVulnerability> vulnerabilities){
        return new ScanResult(scanId, buildId, startTime, duration1, status, vulnerabilities)
    }
}
