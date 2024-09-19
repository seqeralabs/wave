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

package io.seqera.wave.service.mirror.strategy

import io.seqera.wave.service.mirror.MirrorRequest

import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path

import io.seqera.wave.core.ContainerPlatform

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class MirrorStrategyTest extends Specification {

    @Unroll
    def 'should return copy command' () {
        given:
        def strategy = Spy(MirrorStrategy)
        and:
        def request = MirrorRequest.create(
                'source.io/foo',
                'target.io/foo',
                'sha256:12345',
                PLATFORM ? ContainerPlatform.of(PLATFORM) : null,
                Path.of('/workspace'),
                '{auth json}')
        when:
        def cmd = strategy.copyCommand(request)
        then:
        cmd == EXPECTED.tokenize(' ')

        where:
        PLATFORM        | EXPECTED
        null            | "copy --preserve-digests --multi-arch all docker://source.io/foo docker://target.io/foo"
        'linux/amd64'   | "--override-os linux --override-arch amd64 copy --preserve-digests --multi-arch system docker://source.io/foo docker://target.io/foo"
        'linux/arm64'   | "--override-os linux --override-arch arm64 copy --preserve-digests --multi-arch system docker://source.io/foo docker://target.io/foo"
        'linux/arm64/7'| "--override-os linux --override-arch arm64 --override-variant 7 copy --preserve-digests --multi-arch system docker://source.io/foo docker://target.io/foo"

    }

}
