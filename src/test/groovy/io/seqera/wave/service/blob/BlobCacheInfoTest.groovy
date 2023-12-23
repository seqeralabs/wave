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
    }
}
