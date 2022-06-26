package io.seqera.auth

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.util.StringUtils
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.WaveDefault.DOCKER_IO
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
class RegistryLoginServiceImpl implements RegistryLoginService {

    private HttpClient httpClient
    private Map<String,String> tokenCache = new ConcurrentHashMap()

    @Inject
    private RegistryLookupService lookupService

    RegistryLoginServiceImpl() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()
    }

    boolean login(String registryName, String username, String password) {
        if( !registryName )
            registryName = DOCKER_IO
        final registry = lookupService.lookup(registryName)
        if( !registry )
            throw new IllegalArgumentException("Unable to find authorization info for registry: $registryName")

        final basic =  "$username:$password".bytes.encodeBase64()
        HttpRequest request = HttpRequest.newBuilder()
                .uri(registry.auth.realm)
                .GET()
                .header("Authorization", "Basic $basic")
                .build()
        // make the request
        final response = httpClient
                .send(request, HttpResponse.BodyHandlers.ofString())

        if( response.statusCode() == 200 ) {
            log.debug "Container registry '$registryName' login - response: ${response.body()}"
            return true
        }
        else {
            log.debug "Container registry '$registryName' login FAILED: ${response.statusCode()} - response: ${response.body()}"
            return false
        }
    }


    @Override
    void cleanTokenFor(String image) {
        if( tokenCache.containsKey(image) )
            tokenCache.remove(image)
    }

    private HttpRequest makeRequest(String uri, RegistryCredentials creds) {
        final builder = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(uri))
        if( creds?.username && creds.password  ) {
            final basic = "${creds.username}:${creds.password}".bytes.encodeBase64()
            builder.setHeader("Authorization", "Basic $basic")
        }
        return builder.build()
    }
    @Override
    String getTokenFor(String image, RegistryAuth auth, RegistryCredentials creds) {

        if( tokenCache.containsKey(image) )
            return tokenCache[image]

        final login = "${auth.realm}?service=${auth.service}&scope=repository:${image}:pull"
        final req = makeRequest(login, creds)
        log.debug "Token request=$req"

        HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
        final body = resp.body()
        final json = (Map) new JsonSlurper().parseText(body)
        if( resp.statusCode()==200 ) {
            tokenCache[image] = json.token.toString()
            return json.token
        }

        throw new IllegalStateException("Unable to authorize request: $login -- response: $body")
    }

    @Override
    boolean validateUser(String registryName, String user, String password){
        try {
            final result = login(registryName, user, password)
            log.debug "Validate registry credentials userName=$user; password=${StringUtils.redact(password)}; registry=$registryName; host=$registryName; -> result=$result"
            return result
        }
        catch (Exception e) {
            log.error "Enable to validate registry credentials userName=$user; password=${StringUtils.redact(password)}; registry=$registryName; host=$registryName -- cause: ${e.message}", e
            return false
        }
    }
}
