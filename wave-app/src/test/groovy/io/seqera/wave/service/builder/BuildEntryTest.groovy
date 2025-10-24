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

package io.seqera.wave.service.builder

import spock.lang.Specification

import java.nio.file.Path

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildEntryTest extends Specification {

    def 'should create entry with constructor'() {
        given:
        def request = Mock(BuildRequest)
        def result = Mock(BuildResult)
        when:
        def entry = new BuildEntry(request, result)
        then:
        entry.request == request
        entry.result == result
    }

    def 'should create entry with result'() {
        given:
        def request = Mock(BuildRequest)
        def result1 = Mock(BuildResult)
        def result2 = Mock(BuildResult)
        when:
        def entry1 = new BuildEntry(request, result1)
        then:
        entry1.request == request
        entry1.result == result1

        when:
        def entry2 = entry1.withResult(result2)
        then:
        entry1.request == request
        entry1.result == result1
        and:
        entry2.request == request
        entry2.result == result2
        and:
        result1 != result2
    }

    def 'should  create entry with factory' () {
        given:
        def request = new BuildRequest(
                containerId: '12345',
                buildId: "bd-12345_1",
                containerFile: 'FROM foo',
                workspace: Path.of("/some/path"),
                targetImage: 'some/target:12345',
                identity: PlatformId.NULL,
                platform: ContainerPlatform.DEFAULT,
                cacheRepository: 'cacherepo',
                ip: "1.2.3.4",
                configJson: '{"config":"json"}',
                scanId: 'scan12345',
        )
        when:
        def entry = BuildEntry.create(request)
        then:
        entry.request == request
        entry.result == BuildResult.create(request)
    }
}
