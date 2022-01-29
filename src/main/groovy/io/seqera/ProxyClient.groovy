package io.seqera

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodyHandler
import java.time.Duration

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.Memoized

/**
 *
 * https://www.baeldung.com/java-9-http-client
 * https://openjdk.java.net/groups/net/httpclient/recipes.html
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ProxyClient {

    private String image
    private String username
    private String password

    private String registryName = 'registry-1.docker.io'
    private HttpClient httpClient

    ProxyClient(String image, String username, String password ) {
        this.image = image
        this.username = username
        this.password = password
        init()
    }

    private void init() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build()
    }

    @Memoized
    String getToken() {
        assert username
        assert password
        assert image

        final basic = "$username:$password".bytes.encodeBase64()
        final auth = "Basic $basic"

        final req0 = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create("https://auth.docker.io/token?service=registry.docker.io&scope=repository:${image}:pull"))
                .setHeader("Authorization", auth.toString()) // add resp header
                .build()

        HttpResponse<String> resp = httpClient.send(req0, HttpResponse.BodyHandlers.ofString());
        final body = resp.body()
        final json = (Map) new JsonSlurper().parseText(body)
        if( resp.statusCode()==200 )
            return json.token
        else
            throw new IllegalStateException("Unable to authorize request -- response: $body")
    }

    HttpResponse<String> getString(String path, Map<String,List<String>> headers=null) {
        assert path.startsWith('/'), "Request past should start with a slash character -- offending path: $path"
        return get( URI.create("https://$registryName$path"), headers, HttpResponse.BodyHandlers.ofString() )
    }

    HttpResponse<InputStream> getStream(String path, Map<String,List<String>> headers=null) {
        assert path.startsWith('/'), "Request past should start with a slash character -- offending path: $path"
        return get( URI.create("https://$registryName$path"), headers, HttpResponse.BodyHandlers.ofInputStream() )
    }

    HttpResponse<byte[]> getBytes(String path, Map<String,List<String>> headers=null) {
        assert path.startsWith('/'), "Request past should start with a slash character -- offending path: $path"
        return get( URI.create("https://$registryName$path"), headers, HttpResponse.BodyHandlers.ofByteArray() )
    }

    def <T> HttpResponse<T> get(URI uri, Map<String,List<String>> headers, BodyHandler<T> handler) {

        final builder = HttpRequest.newBuilder() .GET() .uri(uri)
        if( headers ) {
            for( Map.Entry<String,List<String>> entry : headers )
                for( String val : entry.value )
                    builder.header(entry.key, val)
        }
        // add authorisation header
        builder.setHeader("Authorization", "Bearer ${getToken()}")

        final request = builder.build()
        return httpClient.send(request, handler)
    }

}
