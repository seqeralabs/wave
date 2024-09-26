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

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.service.scan.ScanEntry
import io.seqera.wave.service.scan.ScanVulnerability
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class WaveScanRecordTest extends Specification {

    def 'should create wave scan record' () {
        given:
        def startTime = Instant.now()
        def duration = Duration.ofMinutes(2)
        def scanId = '12345'
        def buildId = "testbuildid"
        def containerImage = "testcontainerimage"
        def scanVulnerability = new ScanVulnerability(
                                "id1",
                                "low",
                                "title",
                                "pkgname",
                                "installed.version",
                                "fix.version",
                                "url")
        def vulns = List.of(scanVulnerability)

        when:
        def scanResult= new ScanEntry(
                scanId,
                buildId,
                containerImage,
                startTime,
                duration,
                'SUCCEEDED',
                vulns)
        then:
        scanResult.scanId == scanId
        scanResult.buildId == buildId
        scanResult.containerImage == containerImage
        scanResult.isCompleted()
        scanResult.isSucceeded()
        scanResult.done()

        when:
        def scanRecord = new WaveScanRecord(scanId, scanResult)

        then:
        scanRecord.id == scanId
        scanRecord.buildId == buildId
        scanRecord.vulnerabilities[0] == scanVulnerability
    }

}
