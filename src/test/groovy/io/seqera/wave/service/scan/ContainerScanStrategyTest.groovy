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

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration

import io.seqera.wave.configuration.ScanConfig

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ContainerScanStrategyTest extends Specification {

    def "should return trivy command"() {
        given:
        def targetImage = "respository/scantool"
        def containerScanStrategy = Spy(ScanStrategy)
        def outFile = Path.of('/some/out.json')
        def config = Mock(ScanConfig) { getTimeout() >> Duration.ofMinutes(100) }

        when:
        def command = containerScanStrategy.scanCommand(targetImage, outFile, config)
        then:
        command == [ '--quiet',
                     'image',
                     '--timeout',
                     '100m',
                     '--format',
                     'json',
                     '--output',
                     '/some/out.json',
                     targetImage]
    }

    def "should return trivy command with severity"() {
        given:
        def targetImage = "respository/scantool"
        def containerScanStrategy = Spy(ScanStrategy)
        def outFile = Path.of('/some/out.json')
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(100)
            getSeverity() >> 'low,high'
        }

        when:
        def command = containerScanStrategy.scanCommand(targetImage, outFile, config)
        then:
        command == [ '--quiet',
                     'image',
                     '--timeout',
                     '100m',
                     '--format',
                     'json',
                     '--output',
                     '/some/out.json',
                     '--severity',
                     'low,high',
                     targetImage]
    }
}
