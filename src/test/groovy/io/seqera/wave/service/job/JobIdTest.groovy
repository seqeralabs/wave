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

import java.time.Instant

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JobIdTest extends Specification {

    def 'should create job id' () {
        given:
        def ts = Instant.parse('2024-08-18T19:23:33.650722Z')

        when:
        def job = new JobId(JobId.Type.Transfer, 'foo', ts)
        then:
        job.id == 'foo'
        job.schedulerId == 'transfer-8e5e0d3b81e48cac'
        job.creationTime == ts
        job.type == JobId.Type.Transfer

    }

    def 'should create transfer job' () {
        when:
        def job = JobId.transfer('abc-123')
        then:
        job.id == 'abc-123'
        job.schedulerId =~ /transfer-.+/
        job.type == JobId.Type.Transfer
    }

    def 'should create build job' () {
        when:
        def job = JobId.build('abc-123')
        then:
        job.id == 'abc-123'
        job.schedulerId =~ /build-.+/
        job.type == JobId.Type.Build
    }

    def 'should create scan job' () {
        when:
        def job = JobId.scan('abc-123')
        then:
        job.id == 'abc-123'
        job.schedulerId =~ /scan-.+/
        job.type == JobId.Type.Scan
    }

}
