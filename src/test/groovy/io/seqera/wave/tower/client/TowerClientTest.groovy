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

package io.seqera.wave.tower.client

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TowerClientTest extends Specification {

    def 'should create consistent hash' () {
        given:
        def client = new TowerClient()
       
        expect:
        client.makeKey('a') == '92cf27ac76c18d8e'
        and:
        client.makeKey('a') == client.makeKey('a')
        and:
        client.makeKey('a','b','c') == client.makeKey('a','b','c')
        and:
        client.makeKey('a','b',null) == client.makeKey('a','b',null)
        and:
        client.makeKey(new URI('http://foo.com')) == client.makeKey('http://foo.com')
        and:
        client.makeKey(100l) == client.makeKey('100')
    }

}