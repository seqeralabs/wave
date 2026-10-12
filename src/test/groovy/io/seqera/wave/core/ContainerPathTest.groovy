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

package io.seqera.wave.core

import spock.lang.Specification
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ContainerPathTest extends Specification {

    def 'should validate match registry' () {

        given:
        def reg = REGISTRY
        def foo = new ContainerPath() {

            @Override
            String getRegistry() { return reg }

            @Override
            String getImage() { null }

            @Override
            String getRepository() { null }

            @Override
            String getReference() { null }

            @Override
            String getTargetContainer() { null }
        }
        expect:
        foo.sameRegistry(REPO) == EXPECTED

        where:
        REPO            | REGISTRY          | EXPECTED
        null            | 'foo.com'         | false
        'this/that'     | 'foo.com'         | false
        'foo.com/foo'   | 'foo.com'         | true
        'foo.commmmm'   | 'foo.com'         | false
    }

}
