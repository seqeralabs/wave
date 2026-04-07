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
 * @author Paolo Di Tommaso
 */
class ScanStrategyTest extends Specification {

    def 'should build scan command for container image without severity'() {
        given:
        def strategy = Spy(ScanStrategy)
        def image = 'ubuntu:latest'
        def workDir = Path.of('/tmp/scan')
        def platform = new ContainerPlatform('linux', 'amd64', null)
        def scanConfig = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(15)
            getSeverity() >> null
        }

        when:
        def result = strategy.buildScanCommand(image, workDir, platform, scanConfig)

        then:
        result == [
            '/usr/local/bin/scan.sh',
            '--type', 'container',
            '--target', 'ubuntu:latest',
            '--work-dir', '/tmp/scan',
            '--platform', 'linux/amd64',
            '--timeout', '15',
            '--format', 'default',
            '--cache-dir', '/root/.cache/'
        ]
    }

    def 'should build scan command for container image with severity'() {
        given:
        def strategy = Spy(ScanStrategy)
        def image = 'nginx:alpine'
        def workDir = Path.of('/work/dir')
        def platform = new ContainerPlatform('linux', 'arm64', null)
        def scanConfig = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(20)
            getSeverity() >> 'HIGH,CRITICAL'
        }

        when:
        def result = strategy.buildScanCommand(image, workDir, platform, scanConfig)

        then:
        result == [
            '/usr/local/bin/scan.sh',
            '--type', 'container',
            '--target', 'nginx:alpine',
            '--work-dir', '/work/dir',
            '--platform', 'linux/arm64',
            '--timeout', '20',
            '--format', 'default',
            '--cache-dir', '/root/.cache/',
            '--severity', 'HIGH,CRITICAL'
        ]
    }

    def 'should build scan command for plugin with platform set to none'() {
        given:
        def strategy = Spy(ScanStrategy)
        def image = 'nextflow/plugin:nf-hello@1.0.0'
        def workDir = Path.of('/plugin/scan')
        def platform = new ContainerPlatform('linux', 'amd64', null)
        def scanConfig = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(10)
            getSeverity() >> null
        }

        when:
        def result = strategy.buildScanCommand(image, workDir, platform, scanConfig)

        then:
        result == [
            '/usr/local/bin/scan.sh',
            '--type', 'plugin',
            '--target', 'nextflow/plugin:nf-hello@1.0.0',
            '--work-dir', '/plugin/scan',
            '--platform', 'none',
            '--timeout', '10',
            '--format', 'default',
            '--cache-dir', '/root/.cache/'
        ]
    }

    def 'should build scan command for plugin with severity'() {
        given:
        def strategy = Spy(ScanStrategy)
        def image = 'nextflow/plugin:nf-tower@1.5.0'
        def workDir = Path.of('/tmp/plugin')
        def platform = new ContainerPlatform('linux', 'amd64', null)
        def scanConfig = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(30)
            getSeverity() >> 'CRITICAL'
        }

        when:
        def result = strategy.buildScanCommand(image, workDir, platform, scanConfig)

        then:
        result == [
            '/usr/local/bin/scan.sh',
            '--type', 'plugin',
            '--target', 'nextflow/plugin:nf-tower@1.5.0',
            '--work-dir', '/tmp/plugin',
            '--platform', 'none',
            '--timeout', '30',
            '--format', 'default',
            '--cache-dir', '/root/.cache/',
            '--severity', 'CRITICAL'
        ]
    }

    def 'should convert timeout to minutes correctly'() {
        given:
        def strategy = Spy(ScanStrategy)
        def image = 'alpine:latest'
        def workDir = Path.of('/scan')
        def platform = new ContainerPlatform('linux', 'amd64', null)
        def scanConfig = Mock(ScanConfig) {
            getTimeout() >> Duration.ofSeconds(90)
            getSeverity() >> null
        }

        when:
        def result = strategy.buildScanCommand(image, workDir, platform, scanConfig)

        then:
        result.contains('1') // 90 seconds = 1 minute (rounded down)
        result[result.indexOf('--timeout') + 1] == '1'
    }
}
