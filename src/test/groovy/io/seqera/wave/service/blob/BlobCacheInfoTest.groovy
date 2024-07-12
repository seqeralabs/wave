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
        BlobCacheInfo.create('http://foo.com', [:], [:])
                .locationUri == 'http://foo.com'
        and:
        BlobCacheInfo.create('http://foo.com', [:], [:])
                .headers == [:]
        and:
        BlobCacheInfo.create('http://foo.com', [Foo:['alpha'], Bar:['delta', 'gamma', 'omega']], [:])
                .headers == [Foo:'alpha', Bar: 'delta,gamma,omega']

    }

    def 'should find content type' () {
        expect:
        BlobCacheInfo.create('http://foo', [:], HEADERS ).getContentType() == EXPECTED

        where:
        HEADERS                     | EXPECTED
        ['Content-Type': ['alpha']] | 'alpha'
        ['Content-type': ['delta']] | 'delta'
        ['content-type': ['gamma']] | 'gamma'

    }

    def 'should find cache control' () {
        expect:
        BlobCacheInfo.create('http://foo', [:], HEADERS ).getCacheControl() == EXPECTED

        where:
        HEADERS                          | EXPECTED
        ['Cache-Control': ['alpha']]     | 'alpha'
        ['cache-control': ['delta']]     | 'delta'
        ['CACHE-CONTROL': ['gamma']]     | 'gamma'

    }

    def 'should find content length' () {
        expect:
        BlobCacheInfo.create('http://foo', [:], HEADERS ).getContentLength() == EXPECTED

        where:
        HEADERS                          | EXPECTED
        [:]                              | null
        ['Content-Length': ['']]         | null
        ['Content-Length': ['100']]      | 100L
        ['content-length': ['200']]      | 200L

    }

    def 'should complete blob info'  () {
        given:
        def location = 'http://foo.com'
        def headers = [Foo:['something']]
        def response = ['Content-Length':['100'], 'Content-Type':['text'], 'Cache-Control': ['12345']]
        def cache = BlobCacheInfo.create(location, headers, response)

        when:
        def result = cache.completed(0, 'OK')
        then:
        result.headers == [Foo:'something']
        result.locationUri == 'http://foo.com'
        result.creationTime == cache.creationTime
        result.completionTime >= cache.creationTime
        result.exitStatus == 0
        result.logs == 'OK'
        result.contentLength == 100L
        result.contentType == 'text'
        result.cacheControl == '12345'
        and:
        result.done()
        result.succeeded()

        when:
        result = cache.completed(1, 'Oops')
        then:
        result.headers == [Foo:'something']
        result.locationUri == 'http://foo.com'
        result.creationTime == cache.creationTime
        result.completionTime >= cache.creationTime
        result.exitStatus == 1
        result.contentLength == 100L
        result.contentType == 'text'
        result.cacheControl == '12345'
        and:
        result.done()
        !result.succeeded()
    }

    def 'should fail blob info'  () {
        given:
        def location = 'http://foo.com'
        def headers = [Foo:['something']]
        def response = ['Content-Length':['100'], 'Content-Type':['text'], 'Cache-Control': ['12345']]
        def cache = BlobCacheInfo.create(location, headers, response)

        when:
        def result = cache.failed('Oops')
        then:
        result.headers == [Foo:'something']
        result.locationUri == 'http://foo.com'
        result.creationTime == cache.creationTime
        result.completionTime >= cache.creationTime
        result.exitStatus == null
        result.logs == 'Oops'
        result.contentLength == 100L
        result.contentType == 'text'
        result.cacheControl == '12345'
        and:
        result.done()
        !result.succeeded()
    }

    def 'should cache blob info'  () {
        given:
        def location = 'http://foo.com'
        def headers = [Foo:['something']]
        def response = ['Content-Length':['100'], 'Content-Type':['text'], 'Cache-Control': ['12345']]
        and:
        def cache = BlobCacheInfo.create(location, headers, response)
        when:
        def result = cache.cached()
        then:
        result.headers == [Foo:'something']
        result.locationUri == 'http://foo.com'
        result.creationTime == cache.creationTime
        result.completionTime == cache.creationTime
        result.exitStatus == 0
        result.logs == null
        result.cacheControl == '12345'
        result.contentType == 'text'
        result.contentLength == 100L
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
        def headers = [Foo:['something']]
        def response = ['Content-Length':['100'], 'Content-Type':['text'], 'Cache-Control': ['12345']]

        when:
        def result1 = BlobCacheInfo.create('http://foo.com', headers, response)
        then:
        result1.locationUri == 'http://foo.com'
        and:
        result1.headers == [Foo:'something']
        result1.contentType == 'text'
        result1.contentLength == 100L
        result1.cacheControl == '12345'
        
        when:
        def result2 = result1.withLocation('http://bar.com')
        then:
        result2.locationUri == 'http://bar.com'
        and:
        result2.headers == [Foo:'something']
        result2.contentType == 'text'
        result2.contentLength == 100L
        result2.cacheControl == '12345'
    }
}
