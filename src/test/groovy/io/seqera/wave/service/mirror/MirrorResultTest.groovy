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

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User

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
                '{auth json}',
                'scan-123',
                Instant.now(),
                'GMT',
                Mock(PlatformId)
        )

        when:
        def result = MirrorResult.of(request)
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
        def ts = Instant.now()
        def request = MirrorRequest.create(
                'source.io/foo',
                'target.io/foo',
                'sha256:12345',
                Mock(ContainerPlatform),
                Path.of('/workspace'),
                '{auth json}',
                'scan-123',
                ts,
                'utc+1',
                new PlatformId(new User(id: 1, userName: 'me', email: 'me@host.com'))
        )

        when:
        def m1 = MirrorResult.of(request)
        then:
        m1.status == MirrorResult.Status.PENDING
        m1.userId == 1
        m1.userName == 'me'
        m1.userEmail == 'me@host.com'
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
        m2.creationTime == request.creationTime
        m2.offsetId == request.offsetId
        m2.userId == 1
        m2.userName == 'me'
        m2.userEmail == 'me@host.com'
        and:
        m2.status == MirrorResult.Status.COMPLETED
        m2.duration != null
        m2.exitCode == 0
        m2.logs == 'Some logs'
    }

}
