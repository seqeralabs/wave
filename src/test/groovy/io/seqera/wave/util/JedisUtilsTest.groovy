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

package io.seqera.wave.util

import spock.lang.Specification

import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Protocol

class JedisUtilsTest extends Specification {

    def 'should build address' () {
        expect:
        JedisUtils.buildHostAndPort(ADDR) == EXPECTED

        where:
        ADDR                    | EXPECTED
        'foo'                   | new HostAndPort('foo', 6379)
        'bar.com:5000'          | new HostAndPort('bar.com', 5000)
        'redis://bar.com:1234'  | new HostAndPort('bar.com', 1234)
        'rediss://bar.com:1234' | new HostAndPort('bar.com', 1234)
    }

    def 'should report and error' () {
        when:
        JedisUtils.buildHostAndPort('http://foo')
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid Redis address: 'http://foo' - it should match the regex (rediss?://)?(?<host>[^:]+)(:(?<port>\\d+))?"
    }

    def 'should build basic client config' () {
        when:
        def config = JedisUtils.buildClientConfig('redis://foo', null, null)
        then:
        !config.ssl
        !config.password
        config.socketTimeoutMillis == Protocol.DEFAULT_TIMEOUT
        config.connectionTimeoutMillis == Protocol.DEFAULT_TIMEOUT
    }

    def 'should build client config with ssl' () {
        when:
        def config = JedisUtils.buildClientConfig('rediss://foo', 'password', 1000)
        then:
        config.ssl
        config.password == 'password'
        config.socketTimeoutMillis == 1000
        config.connectionTimeoutMillis == 1000
    }
}

