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

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.blob.TransferRequest
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.scan.ScanRequest
import io.seqera.wave.tower.PlatformId
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JobPendingQueueTest extends Specification {

    def 'should encode request for transfer' () {
        given:
        def encoder = JobPendingQueue.encoder()
        and:
        def ts = Instant.parse('2024-08-18T19:23:33.650722Z')
        and:
        def job = JobSpec.build(
                'docker.io/foo:bar',
                '12345',
                ts,
                Duration.ofMinutes(1),
                Path.of('/some/path') )
        and:
        def request = new JobRequest(job, new TransferRequest('12345', ['one','two','three']))

        when:
        def json = encoder.encode(request)
        then:
        def copy = encoder.decode(json)
        and:
        copy == request
    }

    def 'should encode request for build' () {
        given:
        def encoder =  JobPendingQueue.encoder()
        and:
        def ts = Instant.parse('2024-08-18T19:23:33.650722Z')
        and:
        def job = JobSpec.build(
                'docker.io/foo:bar',
                '12345',
                ts,
                Duration.ofMinutes(1),
                Path.of('/some/path') )
        and:
        def build = new BuildRequest(containerId: '123', identity: PlatformId.NULL, platform: ContainerPlatform.of('amd64'))
        and:
        def request = new JobRequest(job, build)

        when:
        def json = encoder.encode(request)
        then:
        def copy = encoder.decode(json)
        and:
        copy == request
    }


    def 'should encode request for scan' () {
        given:
        def encoder =  JobPendingQueue.encoder()
        and:
        def ts = Instant.parse('2024-08-18T19:23:33.650722Z')
        and:
        def job = JobSpec.scan(
                'docker.io/foo:bar',
                '12345',
                ts,
                Duration.ofMinutes(1),
                Path.of('/some/path') )
        and:
        def scan = ScanRequest.of(
                scanId: '123',
                identity: PlatformId.NULL,
                platform: ContainerPlatform.of('amd64'),
                creationTime: ts )
        and:
        def request = new JobRequest(job, scan)

        when:
        def json = encoder.encode(request)
        then:
        def copy = encoder.decode(json)
        and:
        copy == request
    }


    def 'should encode request for mirror' () {
        given:
        def encoder =  JobPendingQueue.encoder()
        and:
        def ts = Instant.parse('2024-08-18T19:23:33.650722Z')
        and:
        def job = JobSpec.mirror(
                'docker.io/foo:bar',
                '12345',
                ts,
                Duration.ofMinutes(1),
                Path.of('/some/path') )
        and:
        def mirror = MirrorRequest.of(
                scanId: '123',
                identity: PlatformId.NULL,
                platform: ContainerPlatform.of('amd64'),
                creationTime: ts )
        and:
        def request = new JobRequest(job, mirror)

        when:
        def json = encoder.encode(request)
        then:
        def copy = encoder.decode(json)
        and:
        copy == request
    }
}
