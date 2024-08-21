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

package io.seqera.wave.controller

import spock.lang.Shared
import spock.lang.Specification

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.exception.SlowDownException
import io.seqera.wave.model.ContentType
import io.seqera.wave.ratelimit.AcquireRequest
import io.seqera.wave.ratelimit.RateLimiterService
import io.seqera.wave.storage.ManifestCacheStore
import io.seqera.wave.test.DockerRegistryContainer
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class RegistryControllerPullLimitTest extends Specification implements DockerRegistryContainer{

    @Singleton
    static class Limit implements RateLimiterService {

        @Override
        void acquireBuild(AcquireRequest request) throws SlowDownException {
            throw new SlowDownException("Request exceeded expected build rate limit")
        }

        @Override
        void acquirePull(AcquireRequest request) throws SlowDownException {
            throw new SlowDownException("Request exceeded expected pull rate limit")
        }

        @Override
        boolean acquireTimeoutCounter(String endpoint) {
            return false
        }
    }

    @Inject
    @Client("/")
    HttpClient client;

    @Inject
    @Shared
    ApplicationContext applicationContext

    @Inject
    ManifestCacheStore storage

    def setupSpec() {
        initRegistryContainer(applicationContext)
    }

    void 'should get manifest'() {
        when:
        HttpRequest request = HttpRequest.GET("/v2/library/hello-world/manifests/sha256:53641cd209a4fecfc68e21a99871ce8c6920b2e7502df0a20671c6fccc73a7c6").headers({h->
            h.add('Accept', ContentType.DOCKER_MANIFEST_V2_TYPE)
            h.add('Accept', ContentType.DOCKER_MANIFEST_V1_JWS_TYPE)
            h.add('Accept', MediaType.APPLICATION_JSON)
        })
        client.toBlocking().exchange(request,String)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.TOO_MANY_REQUESTS
        e.response.body() == '{"errors":[{"code":"TOOMANYREQUESTS","message":"Request exceeded expected pull rate limit"}]}'
    }


}
