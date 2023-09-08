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

package io.seqera.wave.service.persistence

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.seqera.wave.service.scan.ScanResult
import io.seqera.wave.service.scan.ScanVulnerability
import io.seqera.wave.util.StringUtils
/**
 * Model a Wave container scan result
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class WaveScanRecord {
    String id
    String buildId
    Instant startTime
    Duration duration
    String status
    List<ScanVulnerability> vulnerabilities

    /* required by jackson deserialization - do not remove */
    WaveScanRecord() {}

    WaveScanRecord(String id, String buildId, Instant startTime) {
        this.id = StringUtils.surrealId(id)
        this.buildId = buildId
        this.startTime = startTime
    }

    WaveScanRecord(String id, String buildId, Instant startTime, Duration duration, String status, List<ScanVulnerability> vulnerabilities) {
        this.id = StringUtils.surrealId(id)
        this.buildId = buildId
        this.startTime = startTime
        this.duration = duration
        this.status = status
        this.vulnerabilities = vulnerabilities
                ? new ArrayList<ScanVulnerability>(vulnerabilities)
                : List.<ScanVulnerability>of()
    }

    WaveScanRecord(String id, ScanResult scanResult) {
        this.id = StringUtils.surrealId(id)
        this.buildId = scanResult.buildId
        this.startTime = scanResult.startTime
        this.duration = scanResult.duration
        this.status = scanResult.status
        this.vulnerabilities = scanResult.vulnerabilities
                ? new ArrayList<ScanVulnerability>(scanResult.vulnerabilities)
                : List.<ScanVulnerability>of()
    }

    void setId(String id) {
        this.id = StringUtils.surrealId(id)
    }
}
