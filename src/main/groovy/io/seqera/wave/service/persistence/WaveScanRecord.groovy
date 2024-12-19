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
import io.seqera.wave.core.ContainerPlatform
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
class WaveScanRecord implements Cloneable {

    String id
    String buildId
    String mirrorId
    String requestId
    String containerImage
    ContainerPlatform platform
    Instant startTime
    Duration duration
    String status
    List<ScanVulnerability> vulnerabilities
    Integer exitCode
    String logs

    /* required by jackson deserialization - do not remove */
    WaveScanRecord() {}

    WaveScanRecord(
            String id,
            String buildId,
            String mirrorId,
            String requestId,
            String containerImage,
            ContainerPlatform platform,
            Instant startTime,
            Duration duration,
            String status,
            List<ScanVulnerability> vulnerabilities,
            Integer exitCode,
            String logs
    )
    {
        this.id = StringUtils.surrealId(id)
        this.buildId = buildId
        this.mirrorId = mirrorId
        this.requestId = requestId
        this.containerImage = containerImage
        this.platform = platform
        this.startTime = startTime
        this.duration = duration
        this.status = status
        this.vulnerabilities = vulnerabilities
                ? new ArrayList<ScanVulnerability>(vulnerabilities)
                : List.<ScanVulnerability>of()
        this.exitCode = exitCode
        this.logs = sanitize0(logs)
    }

    WaveScanRecord(ScanEntry scan) {
        this.id = StringUtils.surrealId(scan.scanId)
        this.buildId = scan.buildId
        this.mirrorId = scan.mirrorId
        this.requestId = scan.requestId
        this.containerImage = scan.containerImage
        this.platform = scan.platform
        this.startTime = scan.startTime
        this.duration = scan.duration
        this.status = scan.status
        this.vulnerabilities = scan.vulnerabilities
                ? new ArrayList<ScanVulnerability>(scan.vulnerabilities)
                : List.<ScanVulnerability>of()
        this.exitCode = scan.exitCode
        this.logs = sanitize0(scan.logs)
    }

    private static String sanitize0(String str) {
        if( !str )
            return null
        // remove quotes that break sql statement
        str = str.replaceAll(/'/,'')
        if( str.size()>10_000 )
            str = str.substring(0,10_000) + ' [truncated]'
        return str
    }

    void setId(String id) {
        this.id = StringUtils.surrealId(id)
    }

    Boolean succeeded() {
        return duration != null
                ? status == ScanEntry.SUCCEEDED
                : null
    }

    Boolean done() {
        return duration != null
    }

    @Override
    WaveScanRecord clone() {
        return (WaveScanRecord) super.clone()
    }
}
