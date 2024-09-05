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

import java.time.Duration
import java.time.Instant

import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.builder.BuildRequest
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JobFactoryTest extends Specification {

    def 'should create job id' () {
        given:
        def ts = Instant.parse('2024-08-18T19:23:33.650722Z')
        def factory = new JobFactory()
        and:
        def request = new BuildRequest(
                targetImage: 'docker.io/foo:bar',
                buildId: '12345_9',
                startTime: ts,
                maxDuration: Duration.ofMinutes(1)
        )

        when:
        def job = factory.build(request)
        then:
        job.id == 'docker.io/foo:bar'
        job.schedulerId == 'build-12345-9'
        job.creationTime == ts
        job.type == JobSpec.Type.Build
        job.maxDuration == Duration.ofMinutes(1)
        job.getTargetImage() == 'docker.io/foo:bar'
    }

    def 'should create transfer job' () {
        given:
        def duration = Duration.ofMinutes(1)
        def config = new BlobCacheConfig(transferTimeout: duration)
        def factory = new JobFactory(blobConfig:config)

        when:
        def job = factory.transfer('foo-123')
        then:
        job.id == 'foo-123'
        job.schedulerId =~ /transfer-.+/
        job.type == JobSpec.Type.Transfer
        job.maxDuration == duration
    }

}
