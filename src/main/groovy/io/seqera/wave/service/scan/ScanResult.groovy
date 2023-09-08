/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
