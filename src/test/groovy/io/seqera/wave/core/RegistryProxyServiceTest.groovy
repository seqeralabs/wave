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

package io.seqera.wave.core

import java.net.http.HttpHeaders
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionException
import java.util.concurrent.ExecutionException

import io.micronaut.http.HttpStatus
import io.seqera.wave.auth.RegistryInfo
import io.seqera.wave.exception.HttpResponseException
import io.seqera.wave.exception.RegistryForwardException
import io.seqera.wave.exception.RegistryRateLimitException
import io.seqera.wave.proxy.ProxyClient
import io.seqera.wave.WaveDefault
import io.seqera.wave.model.ContainerCoordinates

import io.seqera.wave.proxy.DelegateResponse
import io.seqera.wave.proxy.ProxyCache
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryProxyServiceTest extends Specification {

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject RegistryProxyService registryProxyService


    def 'should retrieve image digest' () {
        given:
        def IMAGE = 'library/hello-world@sha256:6352af1ab4ba4b138648f8ee88e63331aae519946d3b67dae50c313c6fc8200f'
        def request = Mock(BuildRequest)

        when:
        def resp1 = registryProxyService.getImageDigest(request)
        then:
        request.getTargetImage() >> IMAGE
        then:
        resp1 == 'sha256:6352af1ab4ba4b138648f8ee88e63331aae519946d3b67dae50c313c6fc8200f'
    }

    @Requires({System.getenv('AWS_ACCESS_KEY_ID') && System.getenv('AWS_SECRET_ACCESS_KEY')})
    def 'should retrieve image digest on ECR' () {
        given:
        def IMAGE = '195996028523.dkr.ecr.eu-west-1.amazonaws.com/wave/app:1.0.0'
        def request = Mock(BuildRequest)

        when:
        def resp1 = registryProxyService.getImageDigest(request)
        then:
        request.getTargetImage() >> IMAGE
        request.getIdentity() >> new PlatformId()
        then:
        resp1 == 'sha256:d5c169e210d6b789b2dc7eced66471cf4ce2c7260ac7299fbef464ff902086be'
    }

    def 'should return a stable hash' () {
        given:
        def p1 = RoutePath.parse('quay.io/v2/ubuntu/blobs/sha256:123')

        when:
        def k1 = RegistryProxyService.requestKey(p1,  null)
        then:
        k1 == 'a7e1706bf0024934'

        when:
        def k2 = RegistryProxyService.requestKey(p1, [:])
        then:
        k2 == 'a7e1706bf0024934'

        when:
        def k3 = RegistryProxyService.requestKey(p1, ['Content-Type': ['text/1']])
        then:
        k3 == '3a2d860dc46a1bc4'

        when:
        def k4 = RegistryProxyService.requestKey(p1, ['Content-Type': ['text/1', 'text/2']])
        then:
        k4 == 'f9fe81aed4d72cba'

        when:
        def k5 = RegistryProxyService.requestKey(p1, ['Content-Type': ['text/1', 'text/2'], 'X-Foo': ['bar']])
        then:
        k5 == 'f9fe81aed4d72cba'    // <-- the header 'X-Foo' does not alter the cache key

        when:
        def k6 = RegistryProxyService.requestKey(p1, ["cache-control":["none"]])
        then:
        k6 == null
    }

    def 'should not cache partial blob responses' () {
        given:
        def cache = Mock(ProxyCache) { getDuration() >> Duration.ofMinutes(10) }
        def service = new RegistryProxyService(cache: cache)
        def route = RoutePath.v2path('blobs', 'docker.io', 'library/ubuntu', 'sha256:a5d8a12d24a5d8a12d24a5d8a12d24a5d8a12d24a5d8a12d24a5d8a12d24a5d8')

        expect:
        service.cacheTtl(route, new DelegateResponse(statusCode: STATUS, body: BODY, location: LOCATION)) == EXPECTED

        where:
        STATUS | BODY          | LOCATION          | EXPECTED
        200    | 'x'.bytes     | null              | Duration.ofMinutes(10)
        206    | 'x'.bytes     | null              | null
        307    | null          | 'https://s3/blob' | Duration.ofMinutes(10)
        200    | null          | null              | null
    }

    def 'check is cacheable header' () {
        expect:
        RegistryProxyService.isCacheableHeader(KEY) == EXPECTED
        where:
        KEY             | EXPECTED
        null            | false
        ''              | false
        'Foo'           | false
        'accept'        | true
        'Accept'        | true
        'Content-Type'  | true
    }

    def 'should resolve the redirect location on blob head request' () {
        given:
        def route = RoutePath.v2path('blobs', 'quay.io', 'biocontainers/fastqc', 'sha256:a5d8a12d24a5d8a12d24a5d8a12d24a5d8a12d24a5d8a12d24a5d8a12d24a5d8')
        def respHeaders = HttpHeaders.of(LOCATION ? ['Location': [LOCATION]] : [:], (a, b) -> true)
        def resp = Mock(HttpResponse) { statusCode() >> STATUS; headers() >> respHeaders }
        def proxyClient = Mock(ProxyClient) {
            getRegistry() >> new RegistryInfo('quay.io', new URI('https://quay.io'), null)
            head(route.path, [:]) >> resp
        }
        def service = Spy(RegistryProxyService) { client(route) >> proxyClient }

        when:
        def result = service.handleHead(route, [:])
        then:
        result.statusCode == STATUS
        result.location == EXPECTED
        result.headers == respHeaders.map()

        where:
        STATUS | LOCATION                         | EXPECTED
        200    | null                             | null
        307    | '/v2/blobs/sha256:abc'           | 'https://quay.io/v2/blobs/sha256:abc'
        307    | 'https://s3.amazonaws.com/blob'  | 'https://s3.amazonaws.com/blob'
        200    | '/v2/blobs/sha256:abc'           | null
    }

    def 'should detect when platform is unavailable' () {
        expect:
        RegistryProxyService.isPlatformUnavailable(ERROR) == EXPECTED

        where:
        ERROR                                                                                                       | EXPECTED
        null                                                                                                        | false
        new IllegalStateException('boom')                                                                           | false
        new HttpResponseException(HttpStatus.REQUEST_TIMEOUT, 'Timeout error connecting to platform', (Throwable)null) | true
        new HttpResponseException(HttpStatus.SERVICE_UNAVAILABLE, 'Unexpected I/O error')                          | true
        new HttpResponseException(429, 'Too many requests')                                                         | true
        new HttpResponseException(500, 'Server error')                                                              | true
        new HttpResponseException(401, 'Unauthorized')                                                              | false
        new HttpResponseException(404, 'Not found')                                                                 | false
        new ExecutionException(new HttpResponseException(HttpStatus.REQUEST_TIMEOUT, 'Timeout', (Throwable)null))  | true
        new CompletionException(new ExecutionException(new HttpResponseException(503, 'Unavailable')))             | true
        new ExecutionException(new HttpResponseException(403, 'Forbidden'))                                         | false
    }

    def 'should get the image digest or fail when unavailable' () {
        given:
        def service = Spy(RegistryProxyService)
        def IMAGE = 'docker.io/library/ubuntu:24.04'

        when:
        def result = service.getImageDigestOrUnavailable(IMAGE, PlatformId.NULL)
        then:
        1 * service.getImageDigest0(IMAGE, PlatformId.NULL, false) >> { if( RESPONSE instanceof Exception ) throw RESPONSE; CompletableFuture.completedFuture(RESPONSE) }
        and:
        result == EXPECTED

        where:
        RESPONSE                                                                     | EXPECTED
        'sha256:12345'                                                               | 'sha256:12345'
        new ExecutionException(new HttpResponseException(401, 'Unauthorized access')) | null
    }

    def 'should fail with 503 when platform is unavailable' () {
        given:
        def service = Spy(RegistryProxyService)
        def IMAGE = 'docker.io/library/ubuntu:24.04'

        when:
        service.getImageDigestOrUnavailable(IMAGE, PlatformId.NULL)
        then:
        1 * service.getImageDigest0(IMAGE, PlatformId.NULL, false) >> { throw new ExecutionException(new HttpResponseException(HttpStatus.REQUEST_TIMEOUT, 'Timeout error connecting to platform', (Throwable)null)) }
        and:
        def e = thrown(HttpResponseException)
        e.statusCode() == HttpStatus.SERVICE_UNAVAILABLE
        e.message.startsWith("Unable to fetch the registry credentials for image '$IMAGE' from Seqera Platform")
    }

    def 'should return null digest when platform is unavailable' () {
        given:
        def service = Spy(RegistryProxyService)
        def IMAGE = 'docker.io/library/ubuntu:24.04'

        when:
        def result = service.getImageDigest(IMAGE, PlatformId.NULL)
        then:
        1 * service.getImageDigest0(IMAGE, PlatformId.NULL, false) >> { throw new ExecutionException(new HttpResponseException(HttpStatus.REQUEST_TIMEOUT, 'Timeout error connecting to platform', (Throwable)null)) }
        and:
        result == null
    }

    def 'should fail when the registry rate limits the request' () {
        when:
        RegistryProxyService.checkRegistryRateLimit('docker.io', 'python:3.13.1', 429, null)
        then:
        def e = thrown(RegistryRateLimitException)
        e.message == "Container registry 'docker.io' is rate limiting the request for image 'python:3.13.1' - add the registry credentials to the workspace or try again later"
    }

    def 'should not fail when the registry reports any other status' () {
        when:
        RegistryProxyService.checkRegistryRateLimit('docker.io', 'python:3.13.1', STATUS, null)
        then:
        noExceptionThrown()

        where:
        STATUS << [200, 401, 403, 404, 500, 502, 503, 504]
    }

    def 'should report the rate limit only for the source image lookup' () {
        given:
        def service = Spy(RegistryProxyService)
        def IMAGE = 'docker.io/library/python:3.13.1'
        def err = new RegistryRateLimitException('Rate limited')

        when:
        service.getImageDigestOrUnavailable(IMAGE, PlatformId.NULL, true)
        then:
        1 * service.getImageDigest0(IMAGE, PlatformId.NULL, false) >> { throw new ExecutionException(err) }
        and:
        def e = thrown(RegistryRateLimitException)
        e.is(err)
    }

    def 'should return null digest for a rate limited build target lookup' () {
        given:
        def service = Spy(RegistryProxyService)
        def IMAGE = 'docker.io/library/python:3.13.1'

        when:
        def result = service.getImageDigestOrUnavailable(IMAGE, PlatformId.NULL)
        then:
        1 * service.getImageDigest0(IMAGE, PlatformId.NULL, false) >> { throw new ExecutionException(new RegistryRateLimitException('Rate limited')) }
        and:
        result == null
    }

    def 'should return null digest when the registry rate limits the lookup' () {
        given:
        def service = Spy(RegistryProxyService)
        def IMAGE = 'docker.io/library/python:3.13.1'

        when:
        def result = service.getImageDigest(IMAGE, PlatformId.NULL)
        then:
        1 * service.getImageDigest0(IMAGE, PlatformId.NULL, false) >> { throw new ExecutionException(new RegistryRateLimitException('Rate limited')) }
        and:
        result == null
    }

    private RegistryProxyService serviceWithHead(String image, int status, String digest=null) {
        final route = RoutePath.v2manifestPath(ContainerCoordinates.parse(image), PlatformId.NULL)
        final respHeaders = HttpHeaders.of(digest ? ['docker-content-digest': [digest]] : [:], (a, b) -> true)
        final resp = Mock(HttpResponse) { statusCode() >> status; headers() >> respHeaders }
        final proxyClient = Stub(ProxyClient)
        proxyClient.withRetryableHttpErrors(_) >> proxyClient
        proxyClient.head(route.path, WaveDefault.ACCEPT_HEADERS) >> resp
        return Spy(RegistryProxyService) { client(route) >> proxyClient }
    }

    private RegistryProxyService serviceFailingWith(String image, Throwable error) {
        final route = RoutePath.v2manifestPath(ContainerCoordinates.parse(image), PlatformId.NULL)
        return Spy(RegistryProxyService) { client(route) >> { throw error } }
    }

    def 'should fail the digest lookup when the registry head reports a rate limit' () {
        when:
        serviceWithHead(IMAGE, 429).getImageDigest1(IMAGE, PlatformId.NULL, false)
        then:
        def e = thrown(RegistryRateLimitException)
        e.message.startsWith("Container registry '$REGISTRY' is rate limiting the request for image '$IMAGE'")

        where:
        IMAGE                             | REGISTRY
        'docker.io/library/python:3.13.1' | 'docker.io'
        'quay.io/biocontainers/fastqc:1'  | 'quay.io'
    }

    def 'should return the digest lookup result when the registry head reports any other status' () {
        given:
        def IMAGE = 'docker.io/library/python:3.13.1'

        when:
        def result = serviceWithHead(IMAGE, STATUS, DIGEST).getImageDigest1(IMAGE, PlatformId.NULL, false)
        then:
        noExceptionThrown()
        result == DIGEST

        where:
        STATUS | DIGEST
        200    | 'sha256:12345'
        404    | null
        401    | null
        503    | null
    }

    def 'should fail the digest lookup when the registry rate limits the auth token request' () {
        given:
        def IMAGE = 'docker.io/library/python:3.13.1'
        def err = new RegistryForwardException('Unexpected response acquiring token', 429, 'some body', [:])

        when:
        serviceFailingWith(IMAGE, err).getImageDigest1(IMAGE, PlatformId.NULL, false)
        then:
        def e = thrown(RegistryRateLimitException)
        e.message.startsWith("Container registry 'docker.io' is rate limiting the request for image '$IMAGE'")
        e.cause.is(err)
    }

    def 'should rethrow the registry auth error when it is not a rate limit' () {
        given:
        def IMAGE = 'docker.io/library/python:3.13.1'
        def err = new RegistryForwardException('Unexpected response acquiring token', STATUS, 'some body', [:])

        when:
        serviceFailingWith(IMAGE, err).getImageDigest1(IMAGE, PlatformId.NULL, false)
        then:
        def e = thrown(RegistryForwardException)
        e.is(err)

        where:
        STATUS << [401, 403, 404, 500, 503]
    }

}
