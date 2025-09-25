/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
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
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class KubeScanStrategyTest extends Specification {

    def "should return trivy plugin command with default mode"() {
        given:
        def plugin = "repository/plugin"
        def workDir = Path.of('/work/dir')
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(100)
            getSeverity() >> null
        }
        def mode = ScanType.Default

        when:
        def command = ScanStrategy.scanPluginCommand(plugin, workDir, config, mode)

        then:
        command == ["oras pull repository/plugin -o /work/dir/plugin && unzip -u /work/dir/plugin/*.zip -d /work/dir/fs && trivy rootfs --scanners vuln --timeout 100m --format json --output /work/dir/report.json --cache-dir /root/.cache/ /work/dir/fs"]
    }

    def "should return trivy plugin command with severity in default mode"() {
        given:
        def plugin = "repository/plugin"
        def workDir = Path.of('/work/dir')
        def config = Mock(ScanConfig) {
            getTimeout() >> Duration.ofMinutes(100)
            getSeverity() >> 'LOW,HIGH'
        }
        def mode = ScanType.Default

        when:
        def command = ScanStrategy.scanPluginCommand(plugin, workDir, config, mode)

        then:
        command == ["oras pull repository/plugin -o /work/dir/plugin && unzip -u /work/dir/plugin/*.zip -d /work/dir/fs && trivy rootfs --scanners vuln --timeout 100m --format json --output /work/dir/report.json --cache-dir /root/.cache/ --severity LOW,HIGH /work/dir/fs"]
    }

}
