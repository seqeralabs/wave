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

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ScanIdTest extends Specification {

    def 'should create scanid' () {
        ScanId.of("docker.io/foo/bar:latest").toString() == 'sc-057d44d43bb7f81c_0'
    }

    def 'create a count with the specified count' () {
        given:
        def scan = ScanId.of('x')

        expect:
        scan.toString() == 'sc-94be9fbddcc3af8e_0'
        scan.withCount(1).toString() == 'sc-94be9fbddcc3af8e_1'
        scan.withCount(9).toString() == 'sc-94be9fbddcc3af8e_9'

    }

}
