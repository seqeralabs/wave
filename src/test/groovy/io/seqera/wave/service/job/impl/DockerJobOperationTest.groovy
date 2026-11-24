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

package io.seqera.wave.service.job.impl

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

import io.seqera.wave.service.job.JobSpec
import spock.lang.Specification
import spock.lang.Unroll
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DockerJobOperationTest extends Specification {

    @Unroll
    def 'should parse state string' () {
        expect:
        DockerJobOperation.State.parse(STATE) == EXPECTED

        where:
        STATE               | EXPECTED
        'running'           | new DockerJobOperation.State('running')
        'exited'            | new DockerJobOperation.State('exited')
        'exited,'           | new DockerJobOperation.State('exited')
        'exited,0'          | new DockerJobOperation.State('exited', 0)
        'exited,10'         | new DockerJobOperation.State('exited', 10)
    }

    @Unroll
    def 'should check if job is expired' () {
        given:
        def launch = Instant.parse('2026-01-01T00:00:00Z')
        def job = JobSpec.build('rec-1', 'build-1', launch, MAX, Path.of('/work')).withLaunchTime(LAUNCHED ? launch : null)

        expect:
        DockerJobOperation.isExpired(job, launch.plus(ELAPSED)) == EXPECTED

        where:
        LAUNCHED | MAX                      | ELAPSED                  | EXPECTED
        true     | Duration.ofMinutes(10)   | Duration.ofMinutes(5)    | false
        true     | Duration.ofMinutes(10)   | Duration.ofMinutes(10)   | false
        true     | Duration.ofMinutes(10)   | Duration.ofMinutes(11)   | true
        true     | null                     | Duration.ofDays(1)       | false
        false    | Duration.ofMinutes(10)   | Duration.ofDays(1)       | false
    }

}
