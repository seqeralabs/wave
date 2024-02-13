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

package io.seqera.wave.service.blob

import spock.lang.Specification

import java.time.Instant

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlobCacheInfoTest extends Specification {

    def 'should create blob info' () {
        expect:
        BlobCacheInfo.create('http://foo.com', [:])
                .locationUri == 'http://foo.com'
        and:
        BlobCacheInfo.create('http://foo.com', [:])
                .headers == [:]
        and:
        BlobCacheInfo.create('http://foo.com', [Foo:['alpha'], Bar:['delta', 'gamma', 'omega']])
                .headers == [Foo:'alpha', Bar: 'delta,gamma,omega']

        and:
        BlobCacheInfo.create1('http://foo.com', [Foo:'alpha', Bar:'beta'])
                .headers == [Foo:'alpha', Bar: 'beta']

    }

    def 'should find content type' () {
        expect:
        BlobCacheInfo.create1('http:/foo', HEADERS ).getContentType() == EXPECTED

        where:
        HEADERS                     | EXPECTED
        ['Content-Type': 'alpha']     | 'alpha'
        ['Content-type': 'delta']     | 'delta'
        ['content-type': 'gamma']     | 'gamma'

    }

    def 'should find content type' () {
        expect:
        BlobCacheInfo.create1('http:/foo', HEADERS ).getCacheControl() == EXPECTED

        where:
        HEADERS                     | EXPECTED
        ['Cache-Control': 'alpha']     | 'alpha'
        ['cache-control': 'delta']     | 'delta'
        ['CACHE-CONTROL': 'gamma']     | 'gamma'

    }

    def 'should complete blob info'  () {
        given:
        def location = 'http://foo.com'
        def headers = [Foo:'something']
        def cache = BlobCacheInfo.create1(location, headers)

        when:
        def result = cache.completed(0, 'OK')
        then:
        result.headers == headers
        result.locationUri == 'http://foo.com'
        result.creationTime == cache.creationTime
        result.completionTime >= cache.creationTime
        result.exitStatus == 0
        result.logs == 'OK'
        and:
        result.done()
        result.succeeded()


        when:
        result = cache.completed(1, 'Oops')
        then:
        result.headers == headers
        result.locationUri == 'http://foo.com'
        result.creationTime == cache.creationTime
        result.completionTime >= cache.creationTime
        result.exitStatus == 1
        and:
        result.done()
        !result.succeeded()
    }

    def 'should fail blob info'  () {
        given:
        def location = 'http://foo.com'
        def headers = [Foo:'something']
        def cache = BlobCacheInfo.create1(location, headers)
        when:
        def result = cache.failed('Oops')
        then:
        result.headers == headers
        result.locationUri == 'http://foo.com'
        result.creationTime == cache.creationTime
        result.completionTime >= cache.creationTime
        result.exitStatus == null
        result.logs == 'Oops'
        and:
        result.done()
        !result.succeeded()
    }

    def 'should cache blob info'  () {
        given:
        def location = 'http://foo.com'
        def headers = [Foo:'something']
        def cache = BlobCacheInfo.create1(location, headers)
        when:
        def result = cache.cached()
        then:
        result.headers == headers
        result.locationUri == 'http://foo.com'
        result.creationTime == cache.creationTime
        result.completionTime == cache.creationTime
        result.exitStatus == 0
        result.logs == null
        and:
        result.done()
        result.succeeded()
    }

    def 'should unknown blob info'  () {
        given:
        def result = BlobCacheInfo.unknown()
        expect:
        result.headers == null
        result.locationUri == null
        result.creationTime == Instant.ofEpochMilli(0)
        result.completionTime == Instant.ofEpochMilli(0)
        result.exitStatus == null
        result.logs == null
        and:
        !result.done()
        !result.succeeded()

        and:
        result.withLocation('http://foo').locationUri == null
    }

    def 'should change location uri' () {
        given:
        def result = BlobCacheInfo.create('http://foo.com', [:])

        expect:
        result.locationUri == 'http://foo.com'
        result.withLocation('http://bar.com')
                .locationUri == 'http://bar.com'
    }
}
