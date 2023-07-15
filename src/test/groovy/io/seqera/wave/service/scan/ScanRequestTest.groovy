package io.seqera.wave.service.scan

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.core.ContainerPlatform
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
        def build = new BuildRequest('FROM ubuntu', workspace, 'docker.io', null, null, Mock(User), platform, '{json}', null, null, null)

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
