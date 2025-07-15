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

package io.seqera.wave.service.mirror.strategy

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.MirrorConfig

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DockerMirrorStrategyTest extends Specification {

    final testEnv =[
            AWS_ACCESS_KEY_ID: 'test',
            AWS_SECRET_ACCESS_KEY: 'test',
    ]

    def 'should build docker command'  () {
        given:
        def config = new MirrorConfig(skopeoImage: 'skopeo:latest')
        def buildConfig = new BuildConfig(buildWorkspace: 's3://bucket/workspace')
        def strategy = new DockerMirrorStrategy(mirrorConfig: config, buildConfig: buildConfig)

        when:
        def result = strategy.mirrorCmd('foo', 'work/dir', '{"creds:"json"}', testEnv)
        then:
        result == ['docker',
                   'run',
                   '--detach',
                   '--privileged',
                   '--name', 'foo',
                   '-e', 'AWS_ACCESS_KEY_ID=test',
                   '-e', 'AWS_SECRET_ACCESS_KEY=test',
                   '-e', 'DOCKER_CONFIG=/fusion/s3/bucket/workspace/work/dir',
                   '-e', 'REGISTRY_AUTH_FILE=/fusion/s3/bucket/workspace/work/dir/config.json',
                   'skopeo:latest'
        ]

    }

}
