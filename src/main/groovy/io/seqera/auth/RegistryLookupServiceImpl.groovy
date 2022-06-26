package io.seqera.auth

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.TimeUnit

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import groovy.transform.CompileStatic
import jakarta.inject.Singleton
import static io.seqera.WaveDefault.DOCKER_IO
/**
 * Lookup service for container registry. The role of this component
 * is to registry the retrieve the registry authentication realm
 * and service information for an arbitrary registry server.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class RegistryLookupServiceImpl implements RegistryLookupService {

    private HttpClient httpClient

    private CacheLoader<URI, RegistryAuth> loader = new CacheLoader<URI, RegistryAuth>() {
        @Override
        RegistryAuth load(URI endpoint) throws Exception {
            return lookup0(endpoint)
        }
    }

    private LoadingCache<URI, RegistryAuth> cache = CacheBuilder<URI, RegistryAuth>
                .newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(1, TimeUnit.HOURS)
                .build(loader)

    RegistryLookupServiceImpl() {
        httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .build()
    }

    protected RegistryAuth lookup0(URI endpoint) {
        final request = HttpRequest.newBuilder() .uri(endpoint) .GET() .build()
        // make the request
        final response = httpClient .send(request, HttpResponse.BodyHandlers.ofString())
        final code = response.statusCode()
        if( code == 401 ) {
            def authenticate = response.headers().firstValue('WWW-Authenticate').orElse(null)
            def result = RegistryAuth.parse(authenticate)
            if( !result && authenticate?.startsWith('Basic realm=') ) {
                result = new RegistryAuth(endpoint, null, RegistryAuth.Type.Basic)
            }
            return result
        }
        else if( code == 200 ) {
            return new RegistryAuth(endpoint)
        }
        else {
            throw new IllegalArgumentException("Request '$endpoint' unexpected response code: $code; message: ${response.body()} ")
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    RegistryInfo lookup(String registry) {
        final endpoint = registryEndpoint(registry)
        final auth = cache.get(endpoint)
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
            result = 'registry-1.docker.io'
        if( !result.startsWith('http://') && !result.startsWith('https://') )
            result = 'https://' + result
        if( result.endsWith('/v2'))
            result += '/'
        if( !result.endsWith('/v2/') )
            result += '/v2/'
        return new URI(result)
    }

}
