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
        expect:
        ScanId.of("docker.io/foo/bar:latest").toString() == 'sc-057d44d43bb7f81c_0'
    }

    def 'should scope a private scan id to its tenant' () {
        expect: 'a public id is unchanged - the tenant is absent, not null'
        ScanId.of('x').toString() == 'sc-94be9fbddcc3af8e_0'
        ScanId.of('x', 'y').toString() == 'sc-00769af315dcb1fe_0'

        and: 'two tenants scanning the same image never share an id'
        ScanId.of('x', null, 'https://api.tower.nf:1') != ScanId.of('x', null, 'https://api.tower.nf:2')

        and: 'a tenant id is distinct from the public one for the same image'
        ScanId.of('x', null, 'https://api.tower.nf:1') != ScanId.of('x')

        and: 'the same tenant is stable'
        ScanId.of('x', null, 'https://api.tower.nf:1') == ScanId.of('x', null, 'https://api.tower.nf:1')

        and: 'the tenant is distinguished from the digest, not merged into it'
        ScanId.of('x', 'y') != ScanId.of('x', null, 'y')
    }

    def 'create a count with the specified count' () {
        expect:
        ScanId.of('x').toString() == 'sc-94be9fbddcc3af8e_0'
        ScanId.of('x').withCount(1).toString() == 'sc-94be9fbddcc3af8e_1'
        ScanId.of('x').withCount(9).toString() == 'sc-94be9fbddcc3af8e_9'
        and:
        ScanId.of('x', 'y').toString() == 'sc-00769af315dcb1fe_0'
        ScanId.of('x', 'y').withCount(1).toString() == 'sc-00769af315dcb1fe_1'
        ScanId.of('x', 'y').withCount(9).toString() == 'sc-00769af315dcb1fe_9'
    }

}
