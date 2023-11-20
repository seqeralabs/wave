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

package io.seqera.wave.auth

import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

import com.google.common.cache.CacheBuilder
import com.google.common.cache.CacheLoader
import com.google.common.cache.LoadingCache
import com.google.common.util.concurrent.UncheckedExecutionException
import groovy.json.JsonSlurper
import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.seqera.wave.configuration.HttpClientConfig
import io.seqera.wave.http.HttpClientFactory
import io.seqera.wave.util.Retryable
import io.seqera.wave.util.StringUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.WaveDefault.DOCKER_IO
import static io.seqera.wave.WaveDefault.HTTP_SERVER_ERRORS
/**
 * Implement Docker authentication & login service
 *
 * See https://docs.docker.com/registry/spec/auth/token/
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class RegistryAuthServiceImpl implements RegistryAuthService {

    @Inject
    private HttpClientConfig httpConfig

    @Canonical
    @ToString(includePackage = false, includeNames = true)
    static private class CacheKey {
        final String image
        final RegistryAuth auth
        final RegistryCredentials creds
    }

    private CacheLoader<CacheKey, String> loader = new CacheLoader<CacheKey, String>() {
        @Override
        String load(CacheKey key) throws Exception {
            return getToken0(key)
        }
    }

    private LoadingCache<CacheKey, String> cacheTokens = CacheBuilder<CacheKey, String>
                    .newBuilder()
                    .maximumSize(10_000)
                    .expireAfterAccess(1, TimeUnit.HOURS)
                    .build(loader)

    @Inject
    private RegistryLookupService lookupService

    @Inject RegistryCredentialsFactory credentialsFactory


    /**
     * Implements container registry login
     *
     * @param registryName The registry name e.g. docker.io or quay.io
     * @param username The registry username
     * @param password The registry password
     * @return {@code true} if the login was successful or {@code false} otherwise
     */
    boolean login(String registryName, String username, String password) {
        final httpClient = HttpClientFactory.followRedirectsHttpClient()
        // 0. default to 'docker.io' when the registry name is empty
        if( !registryName )
            registryName = DOCKER_IO

        // 1. look up the registry authorisation info for the given registry name
        final registry = lookupService.lookup(registryName)
        log.debug "Registry '$registryName' => auth: $registry"

        // 2. get the registry credentials
        //    this is needed because some services e.g. AWS ECR requires the use of temporary tokens
        final creds = credentialsFactory.create(registryName, username, password)

        // 3. make a request against the authorization "realm" service using basic
        //    credentials to get the login token
        final basic =  "${creds.username}:${creds.password}".bytes.encodeBase64()
        final endpoint = registry.auth.endpoint
        HttpRequest request = HttpRequest.newBuilder()
                .uri(endpoint)
                .GET()
                .header("Authorization", "Basic $basic")
                .build()
        // retry strategy
        final retryable = Retryable
                .<HttpResponse<String>>of(httpConfig)
                .retryIf( (response) -> response.statusCode() in HTTP_SERVER_ERRORS)
                .onRetry((event) -> log.warn("Unable to connect '$endpoint' - event: $event}"))
        // make the request
        final response = retryable.apply(()-> httpClient.send(request, HttpResponse.BodyHandlers.ofString()))
        final body = response.body()
        // check the response
        if( response.statusCode() == 200 ) {
            log.debug "Container registry '$endpoint' login - response: ${StringUtils.trunc(body)}"
            return true
        }
        else {
            log.warn "Container registry '$endpoint' login FAILED: ${response.statusCode()} - response: ${StringUtils.trunc(response.body())}"
            return false
        }
    }

    @Override
    boolean validateUser(String registry, String user, String password){
        try {
            final result = login(registry, user, password)
            log.debug "Validate registry credentials userName=$user; password=${StringUtils.redact(password)}; registry=$registry; host=$registry; => result=$result"
            return result
        }
        catch (Exception e) {
            log.error "Unable to validate registry credentials userName=$user; password=${StringUtils.redact(password)}; registry=$registry; host=$registry -- cause: ${e.message}", e
            return false
        }
    }

    private HttpRequest makeRequest(String uri, RegistryCredentials creds) {
        final builder = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
        if( creds && creds.username && creds.password ) {
            final basic = "${creds.username}:${creds.password}".bytes.encodeBase64()
            log.trace "Request uri=$uri; 'Authorization Basic $basic'"
            builder.setHeader("Authorization", "Basic $basic")
        }
        return builder.build()
    }

    /**
     * Get the authorization header for the given image, registry and credentials.
     * This can be either a bearer token header or a basic auth header.
     *
     * @param image The image name for which the authorisation is needed
     * @param auth The {@link RegistryAuth} information modelling the target registry
     * @param creds The user credentials
     * @return The authorization header including the 'Basic' or 'Bearer' prefix or null if no authentication type is specified
     */
    @Override
    String getAuthorization(String image, RegistryAuth auth, RegistryCredentials creds) throws RegistryUnauthorizedAccessException {
        if( !auth )
            throw new RegistryUnauthorizedAccessException("Missing authentication credentials")

        if( !auth.type )
            return null

        if( auth.type == RegistryAuth.Type.Bearer ) {
            final token = getAuthToken(image, auth, creds)
            return "Bearer $token"
        }

        if( auth.type == RegistryAuth.Type.Basic ) {
            final String basic = creds ? "$creds.username:$creds.password".bytes.encodeBase64() : null
            return basic ? "Basic $basic" : null
        }

        throw new RegistryUnauthorizedAccessException("Unknown authentication type: $auth.type")
    }

    /**
     * Perform the actual token request to the target registry. This is invoked by the
     * cache loader when a key is missed
     *
     * @param key A {@link CacheKey} object holding the information of the image, registry and credentials
     * @return The resulting bearer token to authorise a pull request
     */
    protected String getToken0(CacheKey key) {
        final httpClient = HttpClientFactory.followRedirectsHttpClient()
        final login = buildLoginUrl(key.auth.realm, key.image, key.auth.service)
        final req = makeRequest(login, key.creds)
        log.trace "Token request=$req"

        // retry strategy
        final retryable = Retryable
                .<HttpResponse<String>>of(httpConfig)
                .retryIf( (response) -> ((HttpResponse)response).statusCode() in HTTP_SERVER_ERRORS )
                .onRetry((event) -> log.warn("Unable to connect '$login' - event: $event"))
        // submit http request
        final response = retryable.apply(()-> httpClient.send(req, HttpResponse.BodyHandlers.ofString()))
        // check the response
        final body = response.body()
        if( response.statusCode()==200 ) {
            final result = (Map) new JsonSlurper().parseText(body)
            // note: azure registry returns 'access_token'
            // see also specs https://docs.docker.com/registry/spec/auth/token/#requesting-a-token
            final token = result.get('token') ?: result.get('access_token')
            if( token ) {
                log.trace "Registry auth '$login' => token: ${StringUtils.redact(token)}"
                return token
            }
        }

        throw new RegistryUnauthorizedAccessException("Unable to authorize request: $login", response.statusCode(), body)
    }

    String buildLoginUrl(URI realm, String image, String service){
        String result = "${realm}?scope=repository:${image}:pull"
        if(service) {
            result += "&service=$service"
        }
        return result
    }

    protected String getAuthToken(String image, RegistryAuth auth, RegistryCredentials creds) {
        final key = new CacheKey(image, auth, creds)
        try {
            return cacheTokens.get(key)
        }
        catch (UncheckedExecutionException | ExecutionException e) {
            // this catches the exception thrown in the cache loader lookup
            // and throws the causing exception that should be `RegistryUnauthorizedAccessException`
            throw e.cause
        }
    }

    /**
     * Invalidate a cached authorization token
     *
     * @param image The image name for which the authorisation is needed
     * @param auth The {@link RegistryAuth} information modelling the target registry
     * @param creds The user credentials
     */
    void invalidateAuthorization(String image, RegistryAuth auth, RegistryCredentials creds) {
        final key = new CacheKey(image, auth, creds)
        cacheTokens.invalidate(key)
    }


}
