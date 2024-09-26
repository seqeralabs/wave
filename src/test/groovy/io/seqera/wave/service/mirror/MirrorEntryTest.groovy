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

import io.seqera.wave.api.ScanMode
import io.seqera.wave.core.ContainerPlatform

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class MirrorEntryTest extends Specification {

    def 'should create mirror entry object' () {
        given:
        def request = MirrorRequest.create(
                'source.io/foo',
                'target.io/foo',
                'sha256:12345',
                Mock(ContainerPlatform),
                Path.of('/workspace'),
                '{auth json}',
                'scan-123',
                ScanMode.lazy,
        )

        when:
        def entry = MirrorEntry.of(request)
        then:
        entry.requestId == request.mirrorId
        entry.key == request.targetImage
        and:
        entry.request == request
        and:
        entry.result == MirrorResult.from(request)
    }

    def 'should validate with result' () {
        given:
        def request = MirrorRequest.create(
                'source.io/foo',
                'target.io/foo',
                'sha256:12345',
                Mock(ContainerPlatform),
                Path.of('/workspace'),
                '{auth json}',
                'scan-123',
                ScanMode.lazy,
        )

        when:
        def entry = MirrorEntry.of(request)
        then:
        !entry.done()

        when:
        entry = entry.withResult( entry.result.complete(0, 'All ok') )
        then:
        entry.done()
        entry.result.succeeded()
        entry.result.exitCode == 0
        entry.result.logs == 'All ok'
    }

}
