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

package io.seqera.wave.service.blob.impl

import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.annotation.Property
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Property(name = 'wave.blobCache.enabled', value = 'true')
@Property(name = 'wave.blobCache.storage.bucket', value='s3://foo')
@Property(name = 'wave.blobCache.storage.region', value='eu-west-1')
@MicronautTest
class BlobCacheStoreImplTest extends Specification {

    @Inject
    BlobCacheStore store

    @Inject
    CacheProvider<String, String> provider

    def 'should get and store an entry' () {
        given:
        def key = UUID.randomUUID().toString()
        def info1 = new BlobCacheInfo(BlobCacheInfo.State.CREATED, 'foo')
        def info2 = new BlobCacheInfo(BlobCacheInfo.State.CREATED, 'bar')

        expect:
        store.get(key) == null

        when:
        store.put(key, info1)
        then:
        store.get(key) == info1

        when:
        store.put(key, info2)
        then:
        store.get(key) == info2
        and:
        info1 != info2
    }

    def 'should put an item only if absent' () {
        given:
        def key = UUID.randomUUID().toString()
        def info1 = new BlobCacheInfo(BlobCacheInfo.State.CREATED, 'foo')
        def info2 = new BlobCacheInfo(BlobCacheInfo.State.CREATED, 'bar')

        expect:
        store.putIfAbsent(key, info1)
        and:
        store.get(key) == info1

        and:
        !store.putIfAbsent(key, info2)
        and:
        store.get(key) == info1 // <-- didn't change
    }

    def 'should put an entry with conditional ttl' () {
        given:
        def key = UUID.randomUUID().toString()
        def info_ok = new BlobCacheInfo(BlobCacheInfo.State.CREATED, 'foo')
        def info_err = new BlobCacheInfo(BlobCacheInfo.State.ERRORED, 'foo')
        and:
        def DELAY_ONE = Duration.ofMinutes(1)
        def DELAY_TWO = Duration.ofSeconds(1)
        and:
        def config = Mock(BlobCacheConfig)
        def cache = Spy(new BlobCacheStore(provider))
        cache.@blobConfig = config

        when:
        cache.storeBlob(key, info_ok)
        then:
        // should use 'status duration' for TTL
        1 * config.getStatusDuration() >> DELAY_ONE
        0 * config.getFailureDuration() >> null
        and:
        1 * cache.put(key, info_ok, DELAY_ONE)  >> null

        when:
        cache.storeBlob(key, info_err)
        then:
        // should use 'failure duration' for TTL
        0 * config.getStatusDuration() >> null
        1 * config.getFailureDuration() >> DELAY_TWO
        and:
        1 * cache.put(key, info_err, DELAY_TWO)  >> null

    }

}
