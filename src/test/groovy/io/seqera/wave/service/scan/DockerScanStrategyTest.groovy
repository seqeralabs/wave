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

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
@Property(name="wave.build.workspace",value="/some/build/dir")
class DockerScanStrategyTest extends Specification {

    @Inject
    DockerScanStrategy dockerContainerStrategy

    def 'should get docker command' () {

        when:
        def scanDir = Path.of('/some/scan/dir')
        def config = Path.of("/user/test/build-workspace/config.json")
        def command = dockerContainerStrategy.dockerWrapper('foo-123', scanDir, config, ['FOO=1', 'BAR=2'])

        then:
        command == [
                'docker',
                'run',
                '--detach',
                '--name',
                'foo-123',
                '-w',
                '/some/scan/dir',
                '-v',
                '/some/scan/dir:/some/scan/dir:rw',
                '-v',
                '/some/build/dir/.trivy-cache:/root/.cache/:rw',
                '-v',
                '/user/test/build-workspace/config.json:/root/.docker/config.json:ro',
                '-e',
                'FOO=1',
                '-e',
                'BAR=2'
        ]

    }
}
