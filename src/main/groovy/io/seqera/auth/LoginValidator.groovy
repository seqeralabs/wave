package io.seqera.auth

import groovy.util.logging.Slf4j
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Slf4j
class LoginValidator {


    boolean login(String username, String password, String registry) {

        try {
            HttpClient remoteClient = HttpClient.create(registry.toURL())
            HttpRequest request = HttpRequest.GET("/v2/").basicAuth(username, password)
            HttpResponse response = remoteClient.toBlocking().exchange(request)

            response.status == HttpStatus.OK

        } catch (HttpClientResponseException invalidException) {
            if (invalidException.status != HttpStatus.UNAUTHORIZED) {
                log.error "Error validating login for $username"
                return false
            }
            String wwwAuthenticate = invalidException.response.headers.get('www-authenticate')
            if (!wwwAuthenticate) {
                log.error "Error validating login for $username"
                return false
            }

            def realm = wwwAuthenticate =~ /(?i).+ realm="(?<url>.+)",service="(?<service>.+)"/
            if (!realm.matches()) {
                log.error "Error validating login for $username"
                return false
            }
            URL authUrl = realm.group("url").toURL()
            String host = "$authUrl.protocol://$authUrl.host"
            String path = authUrl.path
            def httpClient = HttpClient.create(host.toURL())

            try {
                HttpRequest request = HttpRequest.GET(path).basicAuth(username, password)
                Map response = httpClient.toBlocking().retrieve(request, Map)
                log.error "valid login for $username"
                return true

            } catch (HttpClientResponseException ignored) {
                log.error "Error validating login for $username"
                return false
            }
        }
    }
}
