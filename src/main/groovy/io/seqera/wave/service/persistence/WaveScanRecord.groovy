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

package io.seqera.wave.service.persistence

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.seqera.wave.service.job.StateRecord
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
class WaveScanRecord implements StateRecord {
    String id
    String buildId
    String containerImage
    Instant startTime
    Duration duration
    String status
    List<ScanVulnerability> vulnerabilities

    boolean done() {
        return duration != null
    }

    /* required by jackson deserialization - do not remove */
    WaveScanRecord() {}

    WaveScanRecord(String id, String buildId, String containerImage, Instant startTime) {
        this.id = StringUtils.surrealId(id)
        this.buildId = buildId
        this.containerImage = containerImage
        this.startTime = startTime
    }

    WaveScanRecord(String id, String buildId, String containerImage, Instant startTime, Duration duration, String status, List<ScanVulnerability> vulnerabilities) {
        this.id = StringUtils.surrealId(id)
        this.buildId = buildId
        this.containerImage = containerImage
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
        this.containerImage = scanResult.containerImage
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
