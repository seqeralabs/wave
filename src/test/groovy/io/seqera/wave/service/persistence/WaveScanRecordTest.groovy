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

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.service.scan.ScanResult
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
        def scanResult= new ScanResult(
                scanId,
                buildId,
                startTime,
                duration,
                'SUCCEEDED',
                vulns)
        then:
        scanResult.id == scanId
        scanResult.buildId == buildId
        scanResult.isCompleted()
        scanResult.isSucceeded()

        when:
        def scanRecord = new WaveScanRecord(scanId, scanResult)

        then:
        scanRecord.id == scanId
        scanRecord.buildId == buildId
        scanRecord.vulnerabilities[0] == scanVulnerability
    }
}
