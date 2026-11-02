/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.configuration

import spock.lang.Specification

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SociConfigTest extends Specification {

    def 'should default the zTOC repo to the build repo' () {
        when:
        def config = new SociConfig(buildRepo: 'reg.io/wave/build')
        config.init()
        then:
        config.repo == 'reg.io/wave/build/soci-ztoc'
    }

    def 'should accept a zTOC repo on the build registry' () {
        when:
        new SociConfig(repo: 'reg.io/wave/ztoc', buildRepo: 'reg.io/wave/build').init()
        then:
        noExceptionThrown()
    }

    def 'should reject a zTOC repo on another registry' () {
        when:
        new SociConfig(repo: 'docker.io/user/ztoc', buildRepo: 'reg.io/wave/build').init()
        then:
        thrown(IllegalStateException)
    }
}
