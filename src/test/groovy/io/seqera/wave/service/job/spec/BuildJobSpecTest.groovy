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

package io.seqera.wave.service.job.spec

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildJobSpecTest extends Specification {

    def 'should create build job spec' () {
        given:
        def id = PlatformId.of(new User(id: 1), Mock(SubmitContainerTokenRequest))
        def ts = Instant.parse('2024-08-18T19:23:33.650722Z')

        when:
        def spec = new BuildJobSpec(
                'docker.io/foo:bar',
                ts,
                Duration.ofMinutes(1),
                'build-12345-1',
                '12345',
                'docker.io/foo:bar',
                id,
                Path.of('/some/path')
        )
        then:
        spec.id == 'docker.io/foo:bar'
        spec.creationTime == ts
        spec.maxDuration == Duration.ofMinutes(1)
        spec.getSchedulerId() == 'build-12345-1'
        spec.getBuildId() == '12345'
        spec.getTargetImage() == spec.id
        spec.identity == id
    }

}
