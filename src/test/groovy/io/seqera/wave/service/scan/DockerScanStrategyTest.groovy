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

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.ScanConfig
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class DockerScanStrategyTest extends Specification {

    final testEnv =[
            AWS_ACCESS_KEY_ID: 'test',
            AWS_SECRET_ACCESS_KEY: 'test',
    ]

    @Inject
    DockerScanStrategy dockerContainerStrategy

    @MockBean(ScanConfig)
    ScanConfig mockScanConfig() {
        Mock(ScanConfig) {
            getCacheDirectory() >> 'some/scan/cache'
        }
    }

    def 'should get docker command' () {

        when:
        def scanDir = '/some/scan/dir'
        def config = "/user/test/build-workspace/config.json"
        def command = dockerContainerStrategy.dockerWrapper('foo-123', scanDir, config, ['FOO=1', 'BAR=2'], testEnv)

        then:
        command == [
                'docker',
                'run',
                '--detach',
                '--name',
                'foo-123',
                '--privileged',
                '-e', 'AWS_ACCESS_KEY_ID=test',
                '-e', 'AWS_SECRET_ACCESS_KEY=test',
                '-e', 'TRIVY_WORKSPACE_DIR=/some/scan/dir',
                '-e', 'TRIVY_CACHE_DIR=/fusion/s3/null/some/scan/cache',
                '-e', 'DOCKER_CONFIG=/some/scan/dir',
                '-e',
                'FOO=1',
                '-e',
                'BAR=2'
        ]

    }
}
