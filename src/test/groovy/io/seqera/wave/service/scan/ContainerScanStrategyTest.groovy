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

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration

import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.core.ContainerPlatform

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ContainerScanStrategyTest extends Specification {

    def "should return trivy command"() {
        given:
        def targetImage = "repository/scantool"
        def containerScanStrategy = Spy(ScanStrategy)
        def workDir = Path.of('/work/dir')
        def config = Mock(ScanConfig) { getTimeout() >> Duration.ofMinutes(100) }
        def platform = ContainerPlatform.DEFAULT

        when:
        def command = containerScanStrategy.scanCommand(targetImage, workDir, platform, config, ScanMode.Default)
        then:
        command == [ 'trivy',
                     '--quiet',
                     'image',
                     '--platform',
                     'linux/amd64',
                     '--timeout',
                     '100m',
                     '--format',
                     'json',
                     '--output',
                     '/work/dir/report.json',
                     targetImage]
    }

    def "should return trivy command with severity"() {
        given:
        def targetImage = "repository/scantool"
        def containerScanStrategy = Spy(ScanStrategy)
        def workDir = Path.of('/work/dir')
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(100)
            getSeverity() >> 'low,high'
        }

        when:
        def command = containerScanStrategy.scanCommand(targetImage, workDir, platform, config, ScanMode.Default)
        then:
        command == [ 'trivy',
                     '--quiet',
                     'image',
                     '--platform',
                     'linux/amd64',
                     '--timeout',
                     '100m',
                     '--format',
                     'json',
                     '--output',
                     '/work/dir/report.json',
                     '--severity',
                     'low,high',
                     targetImage]
    }

    def "should return trivy command with spdx"() {
        given:
        def targetImage = "repository/scantool"
        def containerScanStrategy = Spy(ScanStrategy)
        def workDir = Path.of('/work/dir')
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(100)
            getSeverity() >> 'low,high'
        }

        when:
        def command = containerScanStrategy.scanCommand(targetImage, workDir, platform, config, ScanMode.Spdx)
        then:
        command == [ 'trivy',
                     '--quiet',
                     'image',
                     '--platform',
                     'linux/amd64',
                     '--timeout',
                     '100m',
                     '--format',
                     'spdx-json',
                     '--output',
                     '/work/dir/spdx.json',
                     targetImage]
    }

    def "should return trivy run command"() {
        given:
        def targetImage = "repository/scantool"
        def containerScanStrategy = Spy(ScanStrategy)
        def workDir = Path.of('/work/dir')
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(100)
            getSeverity() >> 'low,high'
        }

        when:
        def command = containerScanStrategy.trivyCommand(targetImage, workDir, platform, config)
        then:
        command == [ '-c', 'trivy --quiet image --platform linux/amd64 --timeout 100m --format json --output /work/dir/report.json --severity low,high repository/scantool && trivy --quiet image --platform linux/amd64 --timeout 100m --format spdx-json --output /work/dir/spdx.json repository/scantool' ]
    }
}
