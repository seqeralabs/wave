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

package io.seqera.wave.service.mirror

import spock.lang.Specification

import java.nio.file.Path
import java.time.Instant

import io.seqera.wave.api.BuildStatusResponse
import io.seqera.wave.core.ContainerPlatform
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class MirrorResultTest extends Specification {

    def 'should create a result from a request' () {
        given:
        def request = MirrorRequest.create(
                'source.io/foo',
                'target.io/foo',
                'sha256:12345',
                Mock(ContainerPlatform),
                Path.of('/workspace'),
                '{auth json}' )

        when:
        def result = MirrorResult.from(request)
        then:
        result.mirrorId == request.mirrorId
        result.digest == request.digest
        result.platform == request.platform
        result.sourceImage == request.sourceImage
        result.targetImage == request.targetImage
        result.creationTime == request.creationTime
        result.status == MirrorResult.Status.PENDING
        and:
        result.duration == null
        result.exitCode == null
        result.logs == null
    }

    def 'should complete a result result' () {
        given:
        def request = MirrorRequest.create(
                'source.io/foo',
                'target.io/foo',
                'sha256:12345',
                Mock(ContainerPlatform),
                Path.of('/workspace'),
                '{auth json}'  )

        when:
        def m1 = MirrorResult.from(request)
        then:
        m1.status == MirrorResult.Status.PENDING
        m1.duration == null
        m1.exitCode == null
        m1.logs == null

        when:
        def m2 = m1.complete(0, 'Some logs')
        then:
        m2.mirrorId == request.mirrorId
        m2.digest == request.digest
        m2.sourceImage == request.sourceImage
        m2.targetImage == request.targetImage
        m2.creationTime == request.creationTime
        m2.platform == request.platform
        and:
        m2.status == MirrorResult.Status.COMPLETED
        m2.duration != null
        m2.exitCode == 0
        m2.logs == 'Some logs'
    }

    def 'should convert to status response' () {
        when:
        def result1 = new MirrorResult('mr-123', 'sha256:12345', 'source/foo', 'target/foo', Mock(ContainerPlatform), Instant.now())
        def resp = result1.toStatusResponse()
        then:
        resp.id == result1.mirrorId
        resp.status == BuildStatusResponse.Status.PENDING
        resp.startTime == result1.creationTime
        and:
        resp.duration == null
        resp.succeeded == null

        when:
        final result2 = result1.complete(1, 'Some error')
        final resp2 = result2.toStatusResponse()
        then:
        resp2.duration == result2.duration
        resp2.succeeded == false

        when:
        final result3 = result1.complete(0, 'OK')
        final resp3 = result3.toStatusResponse()
        then:
        resp3.duration == result3.duration
        resp3.succeeded == true

    }
}
