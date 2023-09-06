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

import io.seqera.wave.api.BuildContext
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildFormat
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.tower.User
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ScanRequestTest extends Specification {

    def 'should create a scan request' () {
        given:
        def workspace = Path.of('/some/workspace')
        def platform = ContainerPlatform.of('amd64')
        def build = new BuildRequest('FROM ubuntu', workspace, 'docker.io', null, null, BuildFormat.DOCKER, Mock(User), Mock(ContainerConfig), Mock(BuildContext), platform, '{json}', null, null, "", null)

        when:
        def scan = ScanRequest.fromBuild(build)
        then:
        scan.id == build.scanId
        scan.buildId == build.id
        scan.workDir != build.workDir
        scan.configJson == build.configJson
        scan.targetImage == build.targetImage
        scan.platform == build.platform
        scan.workDir.startsWith(workspace)
    }

}
