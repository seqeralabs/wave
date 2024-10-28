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

import spock.lang.Shared
import spock.lang.Specification

import java.time.Duration
import java.time.Instant

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BlobCacheInfoTest extends Specification {

    def 'should create blob info' () {
        when:
        def blob = BlobEntry.create('http://foo.com', 's3://foo/com', [:], [:])
        then:
        blob.locationUri == 'http://foo.com'
        blob.objectUri == 's3://foo/com'
        blob.headers == [:]
        blob.getKey() == 's3://foo/com'
        blob.state == BlobEntry.State.CREATED

        expect:
        BlobEntry.create('http://foo.com', 's3://foo/com', [Foo:['alpha'], Bar:['delta', 'gamma', 'omega']], [:])
                .headers == [Foo:'alpha', Bar: 'delta,gamma,omega']

    }

    def 'should find content type' () {
        expect:
        BlobEntry.create('http://foo', 's3://foo/com', [:], HEADERS ).getContentType() == EXPECTED

        where:
        HEADERS                     | EXPECTED
        ['Content-Type': ['alpha']] | 'alpha'
        ['Content-type': ['delta']] | 'delta'
        ['content-type': ['gamma']] | 'gamma'

    }

    def 'should find cache control' () {
        expect:
        BlobEntry.create('http://foo', 's3://foo/com', [:], HEADERS ).getCacheControl() == EXPECTED

        where:
        HEADERS                          | EXPECTED
        ['Cache-Control': ['alpha']]     | 'alpha'
        ['cache-control': ['delta']]     | 'delta'
        ['CACHE-CONTROL': ['gamma']]     | 'gamma'

    }

    def 'should find content length' () {
        expect:
        BlobEntry.create('http://foo', 's3://foo/com', [:], HEADERS ).getContentLength() == EXPECTED

        where:
        HEADERS                          | EXPECTED
        [:]                              | null
        ['Content-Length': ['']]         | null
        ['Content-Length': ['100']]      | 100L
        ['content-length': ['200']]      | 200L

    }

    def 'should complete blob info'  () {
        given:
        String location = 'http://foo.com'
        String object = 's3://foo/bar'
        def headers = [Foo:['something']]
        def response = ['Content-Length':['100'], 'Content-Type':['text'], 'Cache-Control': ['12345']]
        def cache = BlobEntry.create(location, object, headers, response)

        when:
        def result = cache.completed(0, 'OK')
        then:
        result.headers == [Foo:'something']
        result.locationUri == 'http://foo.com'
        result.objectUri == 's3://foo/bar'
        result.creationTime == cache.creationTime
        result.completionTime >= cache.creationTime
        result.exitStatus == 0
        result.logs == 'OK'
        result.contentLength == 100L
        result.contentType == 'text'
        result.cacheControl == '12345'
        and:
        result.getKey() == 's3://foo/bar'
        and:
        result.done()
        result.succeeded()

        when:
        result = cache.completed(1, 'Oops')
        then:
        result.headers == [Foo:'something']
        result.locationUri == 'http://foo.com'
        result.objectUri == 's3://foo/bar'
        result.creationTime == cache.creationTime
        result.completionTime >= cache.creationTime
        result.exitStatus == 1
        result.contentLength == 100L
        result.contentType == 'text'
        result.cacheControl == '12345'
        and:
        result.getKey() == 's3://foo/bar'
        and:
        result.done()
        !result.succeeded()
    }

    def 'should fail blob info'  () {
        given:
        def location = 'http://foo.com'
        def object = 's3://foo/bar'
        def headers = [Foo:['something']]
        def response = ['Content-Length':['100'], 'Content-Type':['text'], 'Cache-Control': ['12345']]
        def cache = BlobEntry.create(location, object, headers, response)

        when:
        def result = cache.errored('Oops')
        then:
        result.headers == [Foo:'something']
        result.locationUri == 'http://foo.com'
        result.objectUri == 's3://foo/bar'
        result.creationTime == cache.creationTime
        result.completionTime >= cache.creationTime
        result.exitStatus == null
        result.logs == 'Oops'
        result.contentLength == 100L
        result.contentType == 'text'
        result.cacheControl == '12345'
        and:
        result.getKey() == 's3://foo/bar'
        and:
        result.done()
        !result.succeeded()
    }

    def 'should cache blob info'  () {
        given:
        def location = 'http://foo.com'
        def object = 's3://foo/bar'
        def headers = [Foo:['something']]
        def response = ['Content-Length':['100'], 'Content-Type':['text'], 'Cache-Control': ['12345']]
        and:
        def cache = BlobEntry.create(location, object, headers, response)
        when:
        def result = cache.cached()
        then:
        result.headers == [Foo:'something']
        result.locationUri == 'http://foo.com'
        result.objectUri == 's3://foo/bar'
        result.creationTime == cache.creationTime
        result.completionTime == cache.creationTime
        result.exitStatus == 0
        result.logs == null
        result.cacheControl == '12345'
        result.contentType == 'text'
        result.contentLength == 100L
        and:
        result.getKey() == 's3://foo/bar'
        and:
        result.done()
        result.succeeded()
    }

    def 'should unknown blob info'  () {
        given:
        def result = BlobEntry.unknown('Foo bar')
        expect:
        result.headers == null
        result.locationUri == null
        result.objectUri == null
        result.creationTime == Instant.ofEpochMilli(0)
        result.completionTime == Instant.ofEpochMilli(0)
        result.exitStatus == null
        result.logs == 'Foo bar'
        and:
        result.getKey() == null
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
        def result1 = BlobEntry.create('http://foo.com', 's3://foo/bar', headers, response)
        then:
        result1.locationUri == 'http://foo.com'
        result1.objectUri == 's3://foo/bar'
        and:
        result1.headers == [Foo:'something']
        result1.contentType == 'text'
        result1.contentLength == 100L
        result1.cacheControl == '12345'
        result1.getKey() == 's3://foo/bar'
        and:
        
        when:
        def result2 = result1.withLocation('http://bar.com')
        then:
        result2.locationUri == 'http://bar.com'
        result2.objectUri == 's3://foo/bar'
        and:
        result2.getKey() == 's3://foo/bar'
        and:
        result2.headers == [Foo:'something']
        result2.contentType == 'text'
        result2.contentLength == 100L
        result2.cacheControl == '12345'
    }

    @Shared
    Instant now = Instant.now()

    def 'should validate duration' () {
        given:
        def info = new BlobEntry(
                BlobEntry.State.CREATED,
                null,
                null,
                null,
                null,
                null,
                null,
                CREATE,
                COMPLETE )

        expect:
        info.duration() == EXPECTED
        and:
        info.getKey() == null

        where:
        CREATE      | COMPLETE              | EXPECTED
        null        | null                  | null
        now         | null                  | null
        null        | now                   | null
        now         | now.plusSeconds(10)   | Duration.ofSeconds(10)
        now         | now.plusSeconds(60)   | Duration.ofSeconds(60)
    }

    def 'should create blob cached' () {
        given:
        def blob = BlobEntry.create('http://foo.com', 's3://foo/com', [:], [:])

        when:
        def info = blob.cached()
        then:
        info.state == BlobEntry.State.CACHED
        info.locationUri == blob.locationUri
        info.objectUri == blob.objectUri
        info.headers == blob.headers
        info.contentLength == blob.contentLength
        info.contentType == blob.contentType
        info.cacheControl == blob.cacheControl
        info.creationTime == blob.creationTime
        info.completionTime == blob.creationTime
        info.exitStatus == 0
        info.logs == null
    }

    def 'should create blob completed' () {
        given:
        def blob = BlobEntry.create('http://foo.com', 's3://foo/com', [:], [:])

        when:
        def info = blob.completed(1, 'this is the log')
        then:
        info.state == BlobEntry.State.COMPLETED
        info.locationUri == blob.locationUri
        info.objectUri == blob.objectUri
        info.headers == blob.headers
        info.contentLength == blob.contentLength
        info.contentType == blob.contentType
        info.cacheControl == blob.cacheControl
        info.creationTime == blob.creationTime
        info.completionTime <= Instant.now()
        info.exitStatus == 1
        info.logs == 'this is the log'
    }

    def 'should create blob failed' () {
        given:
        def blob = BlobEntry.create('http://foo.com', 's3://foo/com', [:], [:])

        when:
        def info = blob.errored('this is the log')
        then:
        info.state == BlobEntry.State.ERRORED
        info.locationUri == blob.locationUri
        info.objectUri == blob.objectUri
        info.headers == blob.headers
        info.contentLength == blob.contentLength
        info.contentType == blob.contentType
        info.cacheControl == blob.cacheControl
        info.creationTime == blob.creationTime
        info.completionTime <= Instant.now()
        info.exitStatus == null
        info.logs == 'this is the log'
    }

}
