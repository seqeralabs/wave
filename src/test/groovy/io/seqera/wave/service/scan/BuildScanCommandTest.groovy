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
 * Tests for the unified buildScanCommand method
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class BuildScanCommandTest extends Specification {

    def "should build container scan command with all parameters"() {
        given:
        def strategy = Spy(ScanStrategy)
        def targetImage = "alpine:latest"
        def workDir = Path.of('/work/dir')
        def platform = ContainerPlatform.of('linux/amd64')
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(20)
            getSeverity() >> 'CRITICAL,HIGH'
        }

        when:
        def command = strategy.buildScanCommand(targetImage, workDir, platform, config)

        then:
        command == [
            '--type', 'container',
            '--target', 'alpine:latest',
            '--work-dir', '/work/dir',
            '--platform', 'linux/amd64',
            '--timeout', '20',
            '--format', 'default',
            '--severity', 'CRITICAL,HIGH'
        ]
    }

    def "should build container scan command with null platform"() {
        given:
        def strategy = Spy(ScanStrategy)
        def targetImage = "ubuntu:22.04"
        def workDir = Path.of('/scan/path')
        def platform = null
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(15)
            getSeverity() >> 'HIGH'
        }

        when:
        def command = strategy.buildScanCommand(targetImage, workDir, platform, config)

        then:
        // When platform is null, platform.toString() will throw NullPointerException
        thrown(NullPointerException)
    }

    def "should build container scan command without severity"() {
        given:
        def strategy = Spy(ScanStrategy)
        def targetImage = "nginx:alpine"
        def workDir = Path.of('/tmp/scan')
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(10)
            getSeverity() >> null
        }

        when:
        def command = strategy.buildScanCommand(targetImage, workDir, platform, config)

        then:
        command == [
            '--type', 'container',
            '--target', 'nginx:alpine',
            '--work-dir', '/tmp/scan',
            '--platform', 'linux/amd64',
            '--timeout', '10',
            '--format', 'default'
        ]
    }

    def "should build plugin scan command with all parameters"() {
        given:
        def strategy = Spy(ScanStrategy)
        def targetImage = "ghcr.io/seqera-labs/nextflow/plugin/nf-amazon:1.0.0"
        def workDir = Path.of('/work/plugin')
        def platform = ContainerPlatform.of('linux/arm64')
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(25)
            getSeverity() >> 'LOW,MEDIUM,HIGH,CRITICAL'
        }

        when:
        def command = strategy.buildScanCommand(targetImage, workDir, platform, config)

        then:
        // Plugin scan includes platform as 'none' for plugin scans
        command == [
            '--type', 'plugin',
            '--target', 'ghcr.io/seqera-labs/nextflow/plugin/nf-amazon:1.0.0',
            '--work-dir', '/work/plugin',
            '--platform', 'none',
            '--timeout', '25',
            '--format', 'default',
            '--severity', 'LOW,MEDIUM,HIGH,CRITICAL'
        ]
    }

    def "should build plugin scan command without severity"() {
        given:
        def strategy = Spy(ScanStrategy)
        def targetImage = "nextflow/plugin/nf-tower:2.0.0"
        def workDir = Path.of('/plugin/scan')
        def platform = null
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(30)
            getSeverity() >> null
        }

        when:
        def command = strategy.buildScanCommand(targetImage, workDir, platform, config)

        then:
        // Plugin scan includes platform as 'none' for plugin scans
        command == [
            '--type', 'plugin',
            '--target', 'nextflow/plugin/nf-tower:2.0.0',
            '--work-dir', '/plugin/scan',
            '--platform', 'none',
            '--timeout', '30',
            '--format', 'default'
        ]
    }

    def "should detect plugin scan type from image name"() {
        given:
        def strategy = Spy(ScanStrategy)
        def workDir = Path.of('/scan')
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(15)
            getSeverity() >> 'HIGH'
        }

        when:
        def command = strategy.buildScanCommand(pluginImage, workDir, platform, config)

        then:
        command[1] == 'plugin'

        where:
        pluginImage << [
            'nextflow/plugin/nf-amazon',
            'ghcr.io/nextflow/plugin/nf-tower',
            'my.registry.com/nextflow/plugin/custom:1.0'
        ]
    }

    def "should detect container scan type from image name"() {
        given:
        def strategy = Spy(ScanStrategy)
        def workDir = Path.of('/scan')
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(15)
            getSeverity() >> 'HIGH'
        }

        when:
        def command = strategy.buildScanCommand(containerImage, workDir, platform, config)

        then:
        command[1] == 'container'

        where:
        containerImage << [
            'alpine:latest',
            'ubuntu:22.04',
            'gcr.io/project/app:v1.0',
            'my.registry.com/app/service:latest'
        ]
    }

    def "should handle various timeout durations"() {
        given:
        def strategy = Spy(ScanStrategy)
        def targetImage = "alpine:latest"
        def workDir = Path.of('/scan')
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> timeout
            getSeverity() >> null
        }

        when:
        def command = strategy.buildScanCommand(targetImage, workDir, platform, config)

        then:
        command[9] == expectedMinutes

        where:
        timeout                    | expectedMinutes
        Duration.ofMinutes(5)      | '5'
        Duration.ofMinutes(15)     | '15'
        Duration.ofMinutes(30)     | '30'
        Duration.ofMinutes(60)     | '60'
        Duration.ofSeconds(90)     | '1'
        Duration.ofHours(2)        | '120'
    }

    def "should always use 'default' scan format"() {
        given:
        def strategy = Spy(ScanStrategy)
        def targetImage = "alpine:latest"
        def workDir = Path.of('/scan')
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(15)
            getSeverity() >> null
        }

        when:
        def command = strategy.buildScanCommand(targetImage, workDir, platform, config)

        then:
        // Last parameter for container (with platform) should be 'default'
        command.last() == 'default'
    }

    def "should handle empty severity string"() {
        given:
        def strategy = Spy(ScanStrategy)
        def targetImage = "alpine:latest"
        def workDir = Path.of('/scan')
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(15)
            getSeverity() >> ""
        }

        when:
        def command = strategy.buildScanCommand(targetImage, workDir, platform, config)

        then:
        // Empty string is falsy in Groovy, so severity options are not added
        command == [
            '--type', 'container',
            '--target', 'alpine:latest',
            '--work-dir', '/scan',
            '--platform', 'linux/amd64',
            '--timeout', '15',
            '--format', 'default'
        ]
    }

    def "should handle absolute and relative work directory paths"() {
        given:
        def strategy = Spy(ScanStrategy)
        def targetImage = "alpine:latest"
        def platform = ContainerPlatform.DEFAULT
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(15)
            getSeverity() >> null
        }

        when:
        def command = strategy.buildScanCommand(targetImage, workDir, platform, config)

        then:
        command[5] == expectedPath

        where:
        workDir                       | expectedPath
        Path.of('/absolute/path')     | '/absolute/path'
        Path.of('relative/path')      | 'relative/path'
        Path.of('/tmp/scan')          | '/tmp/scan'
        Path.of('./current/dir')      | './current/dir'
    }
}