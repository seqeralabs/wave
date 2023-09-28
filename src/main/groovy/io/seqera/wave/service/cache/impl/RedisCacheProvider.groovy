/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.service.cache.impl

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.apache.commons.codec.digest.DigestUtils
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.params.SetParams
/**
 * Redis based implementation for a {@link CacheProvider}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(property = 'redis.uri')
@Singleton
@CompileStatic
class RedisCacheProvider implements CacheProvider<String,String> {

    @Inject
    private JedisPool pool

    @Override
    String get(String key) {
        try( Jedis conn=pool.getResource() ) {
            return conn.get(key)
        }
    }

    void put(String key, String value, Duration ttl) {
        try( Jedis conn=pool.getResource() ) {
            final params = new SetParams().ex(ttl.toSeconds())
            conn.set(key, value, params)
        }
    }

    @Override
    boolean putIfAbsent(String key, String value, Duration duration) {
        try( Jedis conn=pool.getResource() ) {
            final params = new SetParams().nx().ex(duration.toSeconds())
            final result = conn.set(key, value, params)
            return result == 'OK'
        }
    }

    @Override
    String putIfAbsentAndGetCurrent(String key, String value, Duration ttl) {
        try (Jedis conn = pool.getResource()){
            final params = new SetParams().nx().ex(ttl.toSeconds())
            final tx = conn.multi()
            tx.set(key,value,params)
            tx.get(key)
            final result = tx.exec()
            return result[1].toString()
        }
    }

    @Override
    void remove(String key) {
        try( Jedis conn=pool.getResource() ) {
            conn.del(key)
        }
    }

    @Override
    void clear() {
        try( Jedis conn=pool.getResource() ) {
            conn.flushAll()
        }
    }

    // =============== bi-cache store implementation ===============

    @Override
    void biPut(String key, String value, Duration ttl) {
        final id = DigestUtils.sha256Hex(value)
        try( Jedis conn=pool.getResource() ) {
            final params = new SetParams().nx().ex(ttl.toSeconds())
            final tx = conn.multi()
            tx.set(key, value, params)
            tx.sadd(id, key)
            tx.exec()
        }
    }

    @Override
    void biRemove(String key) {
        try( Jedis conn=pool.getResource() ) {
            final value = conn.get(key)
            final tx = conn.multi()
            tx.del(key)
            if( value ) {
                final id = DigestUtils.sha256Hex(value)
                tx.srem(id, key)
            }
            tx.exec()
        }
    }

    @Override
    Set<String> biKeysFor(String value) {
        final id = DigestUtils.sha256Hex(value)
        try( Jedis conn=pool.getResource() ) {
            return conn.smembers(id)
        }
    }

    @Override
    String biKeyFind(String value, boolean sorted) {
        final id = DigestUtils.sha256Hex(value)
        final list = biKeysFor(value).toList()
        final keys = sorted ? list.toSorted() : list.shuffled()
        final itr = keys.iterator()
        while( itr.hasNext() ) {
            final key = itr.next()
            // verify the key still exists
            if( get(key)!=null )
                return key
            // if the key is not found, remove it from the set
            try( Jedis conn=pool.getResource() ) {
                conn.srem(id, key)
            }
        }
        return null
    }
}
