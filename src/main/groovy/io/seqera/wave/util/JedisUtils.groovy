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

import java.util.regex.Pattern
import redis.clients.jedis.DefaultJedisClientConfig
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.JedisClientConfig


class JedisUtils {

    final static private String ADDRESS_REGEX = '(rediss?://)?(?<host>[^:]+)(:(?<port>\\d+))?'

    final static private Pattern ADDRESS_PATTERN = Pattern.compile(ADDRESS_REGEX)

    static HostAndPort buildHostAndPort(String address) {
        if (!address) {
            throw new IllegalArgumentException("Missing redis address")
        }
        final matcher = ADDRESS_PATTERN.matcher(address)
        if (!matcher.matches()) {
            throw new IllegalArgumentException("Invalid Redis address: '${address}' - it should match the regex $ADDRESS_REGEX")
        }

        final host =  matcher.group('host')
        final port = matcher.group('port')
        return port
                ? new HostAndPort(host, Integer.parseInt(port))
                : new HostAndPort(host, 6379)
    }

    static JedisClientConfig buildClientConfig(String address, String password, Integer timeout) {
        final builder = DefaultJedisClientConfig.builder()
        if(address.startsWith("rediss")){
            builder.ssl(true)
        }
        if (password) {
            builder.password(password)
        }
        if (timeout) {
            builder.socketTimeoutMillis(timeout)
            builder.connectionTimeoutMillis(timeout)
        }
        return builder.build()
    }
}
