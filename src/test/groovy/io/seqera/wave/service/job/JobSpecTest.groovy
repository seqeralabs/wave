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

package io.seqera.wave.service.job

import spock.lang.Specification

import java.nio.file.Path
import java.time.Duration
import java.time.Instant

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JobSpecTest extends Specification {

    def 'should validate constructor' () {
        given:
        def ts = Instant.now()
        when:
        def job = new JobSpec(
                '1234',
                JobSpec.Type.Build,
                'record-123',
                'oper-123',
                ts,
                Duration.ofMinutes(1),
                Path.of('/some/path')
        )
        then:
        job.id == '1234'
        job.recordId == 'record-123'
        job.operationName == 'oper-123'
        job.creationTime == ts
        job.maxDuration == Duration.ofMinutes(1)
        job.workDir == Path.of('/some/path')

    }

    def 'should create transfer job' () {
        given:
        def now = Instant.now()
        def job = JobSpec.transfer('12345','xyz', now, Duration.ofMinutes(1))
        expect:
        job.id
        job.recordId == '12345'
        job.type == JobSpec.Type.Transfer
        job.creationTime == now
        job.maxDuration == Duration.ofMinutes(1)
        job.operationName == 'xyz'
    }

    def 'should create build job' () {
        given:
        def now = Instant.now()
        def job = JobSpec.build('12345','xyz', now, Duration.ofMinutes(1), Path.of('/some/path'))
        expect:
        job.id
        job.recordId == '12345'
        job.type == JobSpec.Type.Build
        job.creationTime == now
        job.maxDuration == Duration.ofMinutes(1)
        job.operationName == 'xyz'
        job.workDir == Path.of('/some/path')
    }

    def 'should create scan job' () {
        given:
        def now = Instant.now()
        def job = JobSpec.scan('12345','xyz', now, Duration.ofMinutes(1), Path.of('/some/path'))
        expect:
        job.id
        job.recordId == '12345'
        job.type == JobSpec.Type.Scan
        job.creationTime == now
        job.maxDuration == Duration.ofMinutes(1)
        job.operationName == 'xyz'
        job.workDir == Path.of('/some/path')
    }
}
