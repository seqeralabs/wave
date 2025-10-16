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

    /**
     * Build unified scan command that works for both container and plugin scans
     * The scan.sh script handles all the complexity internally
     *
     * Currently wave detects whether is a plugin or container scan based on the image name
     * if image name container "nextflow/plugin" then it is a plugin scan otherwise container scan
     * This is a work around we will improve using mediatype in future
     * for more details see https://github.com/seqeralabs/wave/issues/919
     *
     * For container scans: [scanType, image, workDir, platform, timeout, severity, format]
     * For plugin scans:    [scanType, plugin, workDir, timeout, severity, format]
     */
    protected List<String> buildScanCommand(String containerImage, Path workDir, ContainerPlatform platform, ScanConfig scanConfig) {
        final scanType = containerImage.contains("nextflow/plugin") ? "plugin" : "container"

        final platformStr = scanType == 'container' ? platform.toString() : "none"
        final cmd = new ArrayList<String>()
                << '/usr/local/bin/scan.sh'
                << '--type'
                << scanType
                << '--target'
                << containerImage
                << '--work-dir'
                << workDir.toString()
                << '--platform'
                << platformStr
                << '--timeout'
                << "${scanConfig.timeout.toMinutes()}".toString()
                << '--format'
                << 'default'

        if( scanConfig.severity ) {
            cmd << '--severity'
            cmd << scanConfig.severity
        }

        return cmd
    }

    @Deprecated
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

    @Deprecated
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

}
