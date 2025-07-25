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

package io.seqera.wave.auth

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.exception.RegistryForwardException
import io.seqera.wave.http.HttpClientFactory
import io.seqera.util.retry.Retryable
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.WaveDefault.DOCKER_IO
import static io.seqera.wave.WaveDefault.DOCKER_REGISTRY_1
import static io.seqera.wave.auth.RegistryUtils.isServerError
/**
 * Lookup service for container registry. The role of this component
 * is to registry the retrieve the registry authentication realm
 * and service information for an arbitrary registry server.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class RegistryLookupServiceImpl implements RegistryLookupService {

    @Inject
    private HttpClientConfig httpConfig

    @Inject
    @Named(TaskExecutors.BLOCKING)
    private ExecutorService ioExecutor

    @Inject
    private RegistryLookupCache cache

    protected RegistryAuth lookup0(URI endpoint) {
        final httpClient = HttpClientFactory.followRedirectsHttpClient()
        final request = HttpRequest.newBuilder() .uri(endpoint) .GET() .build()
        // retry strategy
        // note: do not retry on 429 error code because it just continues to report the error
        // for a while. better returning the error to the upstream client
        // see also https://github.com/docker/hub-feedback/issues/1907#issuecomment-631028965
        final retryable = Retryable
                .<HttpResponse<String>>of(httpConfig)
                .retryIf((response) -> isServerError(response))
                .onRetry((event) -> log.warn("Unable to connect '$endpoint' - attempt: ${event.attempt} status: ${event.result?.statusCode()}; body: ${event.result?.body()}"))
        // submit the request
        final response = retryable.apply(()-> httpClient.send(request, HttpResponse.BodyHandlers.ofString()))
        // check response
        final code = response.statusCode()
        if( code == 401 ) {
            def authenticate = response.headers().firstValue('WWW-Authenticate').orElse(null)
            log.trace "Authority lookup => endpoint: '$endpoint' - authenticate: '$authenticate'"
            def result = RegistryAuth.parse(authenticate)
            if( !result && authenticate?.startsWith('Basic realm=') ) {
                result = new RegistryAuth(endpoint, null, RegistryAuth.Type.Basic)
            }
            return result
        }
        else if( code == 200 ) {
            return new RegistryAuth(endpoint)
        }
        throw new RegistryForwardException("Unexpected response for '$endpoint' [${response.statusCode()}]", response)
    }

    /**
     * {@inheritDoc}
     */
    @Override
    RegistryInfo lookup(String registry) {
        final endpoint = registryEndpoint(registry)
        final auth = cache.getOrCompute(endpoint.toString(), (k) -> lookup0(endpoint), cache.duration)
        return new RegistryInfo(registry, endpoint, auth)
    }

    /**
     * Given a registry name maps to the corresponding registry URI e.g.
     * quay.io -> https://quay.io/v2/
     *
     * @param registry The registry name e.g. quay.io. When empty defaults to 'docker.io'
     * @return the corresponding registry endpoint uri
     */
    protected URI registryEndpoint(String registry) {
        def result = registry ?: DOCKER_IO
        if( result==DOCKER_IO )
            result = DOCKER_REGISTRY_1
        if( !result.startsWith('http://') && !result.startsWith('https://') )
            result = prefix(result)
        if( result.endsWith('/v2'))
            result += '/'
        if( !result.endsWith('/v2/') )
            result += '/v2/'
        return new URI(result)
    }

    private String prefix(String host) {
        final protocol = host=='localhost' || host.startsWith('localhost:') ? 'http' : 'https'
        return protocol + '://' + host
    }
}
