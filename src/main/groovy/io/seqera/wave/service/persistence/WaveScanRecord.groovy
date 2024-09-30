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
import io.seqera.wave.service.scan.ScanEntry
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
    String containerImage
    Instant startTime
    Duration duration
    String status
    List<ScanVulnerability> vulnerabilities

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

    WaveScanRecord(String id, ScanEntry scanResult) {
        this.id = StringUtils.surrealId(id)
        this.buildId = scanResult.requestId
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

    Boolean succeeded() {
        return duration != null
                ? status == ScanEntry.SUCCEEDED
                : null
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
}
