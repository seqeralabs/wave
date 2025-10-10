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

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.core.ContainerPlatform
/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
abstract class ScanStrategy {

    abstract void scanContainer(String jobName, ScanEntry entry)

    protected List<String> scanCommand(String targetImage, Path workDir, ContainerPlatform platform, ScanConfig config, ScanType mode) {
        List<String> cmd = ['trivy', '--quiet', 'image']
        if( platform ) {
            cmd << '--platform'
            cmd << platform.toString()
        }
        cmd << '--timeout'
        cmd << "${config.timeout.toMinutes()}m".toString()
        cmd << '--format'
        cmd << mode.format
        cmd << '--output'
        cmd << workDir.resolve(mode.output).toString()
        cmd << '--cache-dir'
        cmd << Trivy.CACHE_MOUNT_PATH
        if( config.severity && mode==ScanType.Default ) {
            cmd << '--severity'
            cmd << config.severity
        }
        cmd << targetImage
        return cmd
    }

    protected List<String> trivyCommand(String containerImage, Path workDir, ContainerPlatform platform, ScanConfig scanConfig) {
        final cmd = new ArrayList<String>(50)
        // the vulnerability scan command
        cmd.addAll(scanCommand(containerImage, workDir, platform, scanConfig, ScanType.Default) )
        // command separator
        cmd.add("&&")
        // the SBOM spdx scan
        cmd.addAll(scanCommand(containerImage, workDir, platform, scanConfig, ScanType.Spdx) )
        return List.of(cmd.join(' '))
    }

    protected static List<String> scanPluginCommand(String plugin, Path workDir, ScanConfig config, ScanType mode) {
        final cmd = new ArrayList<String>(50)
        // the vulnerability scan command
        cmd.addAll(trivyPluginCommand(workDir, config, ScanType.Default) )
        // command separator
        cmd.add("&&")
        // the SBOM spdx scan
        cmd.addAll(trivyPluginCommand(workDir, config, ScanType.Spdx) )

        return List.of("oras pull $plugin -o $workDir/plugin && unzip -u $workDir/plugin/*.zip -d $workDir/fs && ${cmd.join(' ')}".toString())
    }

    protected static List<String> trivyPluginCommand(Path workDir, ScanConfig config, ScanType mode) {
        final trivyCmd = ['trivy', 'rootfs', '--scanners vuln']
        trivyCmd
                << '--timeout'
                << "${config.timeout.toMinutes()}m".toString()
                << '--format'
                << mode.format
                << '--output'
                << workDir.resolve(mode.output).toString()
                << '--cache-dir'
                << Trivy.CACHE_MOUNT_PATH
        if( config.severity && mode==ScanType.Default ) {
            trivyCmd << '--severity'
            trivyCmd << config.severity
        }
        trivyCmd << "$workDir/fs".toString()

        return trivyCmd
    }

}
