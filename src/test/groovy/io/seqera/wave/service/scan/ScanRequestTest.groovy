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

import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.tower.PlatformId
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ScanRequestTest extends Specification {

    def 'should create a scan request' () {
        given:
        def workspace = Path.of('/some/workspace')
        def platform = ContainerPlatform.of('amd64')
        def build = new BuildRequest('FROM ubuntu', workspace, 'docker.io', null, null, BuildFormat.DOCKER, Mock(PlatformId), Mock(ContainerConfig), Mock(BuildContext), platform, '{json}', null, null, "", null, null) .withBuildId('1')

        when:
        def scan = ScanRequest.fromBuild(build)
        then:
        scan.id == build.scanId
        scan.buildId == build.buildId
        scan.workDir != build.workDir
        scan.configJson == build.configJson
        scan.targetImage == build.targetImage
        scan.platform == build.platform
        scan.workDir.startsWith(workspace)
    }

}
