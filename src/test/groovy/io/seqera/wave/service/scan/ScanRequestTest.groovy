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

package io.seqera.wave.service.scan

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
class ScanRequestTest extends Specification {

    def 'should create a scan request' () {
        given:
        def timestamp = Instant.now()
        def identity = new PlatformId(new User(id:1))
        when:
        def scan = ScanRequest.of(
                scanId: 'sc-123',
                buildId: 'bd-123',
                mirrorId: 'mr-123',
                requestId: 'rq-123',
                configJson: '{config}',
                targetImage: 'tg-image',
                platform: ContainerPlatform.DEFAULT,
                workDir: Path.of('/some/dir'),
                creationTime: timestamp,
                identity: identity
        )
        
        then:
        scan.scanId == 'sc-123'
        scan.buildId == 'bd-123'
        scan.mirrorId == 'mr-123'
        scan.requestId == 'rq-123'
        scan.configJson == '{config}'
        scan.targetImage == 'tg-image'
        scan.platform == ContainerPlatform.DEFAULT
        scan.workDir == Path.of('/some/dir')
        scan.creationTime == timestamp
        scan.identity == identity
    }

}
