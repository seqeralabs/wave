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

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class MirrorRequestTest extends Specification {

    def 'should create mirror request' () {
        when:
        def ts = Instant.now()
        def req = MirrorRequest.create(
                'docker.io/foo:latest',
                'quay.io/foo:latest',
                'sha256:12345',
                ContainerPlatform.DEFAULT,
                Path.of('/workspace'),
                '{json config}',
                'sc-123',
                ts,
                'utc',
                Mock(PlatformId)
        )
        then:
        req.mirrorId
        req.sourceImage == 'docker.io/foo:latest'
        req.targetImage == 'quay.io/foo:latest'
        req.digest == 'sha256:12345'
        req.platform == ContainerPlatform.DEFAULT
        req.workDir == Path.of("/workspace/${req.mirrorId}")
        req.authJson == '{json config}'
        req.creationTime >= ts
        req.creationTime <= Instant.now()
        req.scanId == 'sc-123'
        req.creationTime == ts
        req.offsetId == 'utc'
    }

}
