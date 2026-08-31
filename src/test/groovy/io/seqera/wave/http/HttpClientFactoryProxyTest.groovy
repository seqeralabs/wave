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

package io.seqera.wave.http

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.KeyStore
import java.time.Duration
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManagerFactory

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import com.sun.net.httpserver.HttpsConfigurator
import com.sun.net.httpserver.HttpsServer
import io.seqera.http.HxProxyConfig
import spock.lang.Requires
import spock.lang.Shared
import spock.lang.Specification
/**
 * Verify the {@link HttpClientFactory} clients authenticate against an egress
 * proxy, for both plain HTTP forwarding and HTTPS CONNECT tunnelling.
 *
 * The target servers are bound to a non-loopback address because loopback targets
 * always bypass the proxy (see {@link HxProxyConfig}).
 *
 * Note: the HTTPS tunnelling test requires the {@code jdk.http.auth.tunneling.disabledSchemes}
 * system property to be set to empty on the test JVM (see the `test` task in `build.gradle`),
 * since by default the JDK disables Basic authentication over CONNECT requests
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires({ HttpClientFactoryProxyTest.localAddress() })
class HttpClientFactoryProxyTest extends Specification {

    static final String USERNAME = 'proxy-user'
    static final String PASSWORD = 'proxy-secret'

    @Shared String targetHost
    @Shared HttpServer httpTarget
    @Shared HttpsServer httpsTarget
    @Shared SSLContext clientSslContext

    static private final String LOCAL_ADDRESS = findLocalAddress()

    static String localAddress() {
        return LOCAL_ADDRESS
    }

    /**
     * @return The first non-loopback IPv4 address of this machine, or {@code null} when none is available
     */
    static private String findLocalAddress() {
        for( NetworkInterface iface : NetworkInterface.networkInterfaces.toList() ) {
            if( !iface.up || iface.loopback )
                continue
            for( InetAddress addr : iface.inetAddresses.toList() ) {
                if( addr instanceof Inet4Address && !addr.loopbackAddress )
                    return addr.hostAddress
            }
        }
        return null
    }

    private static File generateKeystore(String address) {
        final result = File.createTempFile('wave-proxy-test', '.p12')
        result.delete()
        result.deleteOnExit()
        final keytool = System.getProperty('java.home') + File.separator + 'bin' + File.separator + 'keytool'
        final command = [keytool, '-genkeypair', '-alias', 'wave-test', '-keyalg', 'EC', '-groupname', 'secp256r1',
                         '-storetype', 'PKCS12', '-keystore', result.absolutePath, '-storepass', 'changeit',
                         '-dname', 'CN=localhost, O=Wave test', '-ext', "SAN=dns:localhost,ip:127.0.0.1,ip:$address".toString(), '-validity', '7']
        final process = new ProcessBuilder(command).redirectErrorStream(true).start()
        final output = process.inputStream.text
        assert process.waitFor() == 0, "keytool failed: $output"
        return result
    }

    def setupSpec() {
        targetHost = localAddress()
        // plain http target server
        httpTarget = HttpServer.create(new InetSocketAddress(targetHost, 0), 0)
        httpTarget.createContext('/hello', (HttpExchange exchange) -> {
            final body = 'Hello world!'.bytes
            exchange.sendResponseHeaders(200, body.length)
            exchange.responseBody.withCloseable { it.write(body) }
        })
        httpTarget.start()
        // https target server using a self-signed certificate generated on the fly
        final keystore = KeyStore.getInstance('PKCS12')
        generateKeystore(targetHost).withInputStream {
            keystore.load(it, 'changeit'.toCharArray())
        }
        final kmf = KeyManagerFactory.getInstance(KeyManagerFactory.defaultAlgorithm)
        kmf.init(keystore, 'changeit'.toCharArray())
        final serverContext = SSLContext.getInstance('TLS')
        serverContext.init(kmf.keyManagers, null, null)
        httpsTarget = HttpsServer.create(new InetSocketAddress(targetHost, 0), 0)
        httpsTarget.httpsConfigurator = new HttpsConfigurator(serverContext)
        httpsTarget.createContext('/hello', (HttpExchange exchange) -> {
            final body = 'Hello secure world!'.bytes
            exchange.sendResponseHeaders(200, body.length)
            exchange.responseBody.withCloseable { it.write(body) }
        })
        httpsTarget.start()
        // client ssl context trusting the self-signed test certificate
        final trustStore = KeyStore.getInstance('PKCS12')
        trustStore.load(null, null)
        trustStore.setCertificateEntry('wave-test', keystore.getCertificate('wave-test'))
        final tmf = TrustManagerFactory.getInstance(TrustManagerFactory.defaultAlgorithm)
        tmf.init(trustStore)
        clientSslContext = SSLContext.getInstance('TLS')
        clientSslContext.init(null, tmf.trustManagers, null)
    }

    def cleanupSpec() {
        httpTarget?.stop(0)
        httpsTarget?.stop(0)
    }

    def cleanup() {
        // restore the default no-proxy behaviour for the tests run after this spec
        HttpClientFactory.setProxyConfig(null)
    }

    private static HttpRequest getRequest(String uri) {
        return HttpRequest.newBuilder(new URI(uri))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build()
    }

    /**
     * Create a client using the given proxy settings and trusting the test tls certificate
     */
    private HttpClient newTlsClient(HxProxyConfig config) {
        final builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .proxy(config.toProxySelector())
                .sslContext(clientSslContext)
        final auth = config.toAuthenticator()
        if( auth )
            builder.authenticator(auth)
        return builder.build()
    }

    def 'should get 407 response from the #POLICY redirects client when the proxy requires authentication and no credentials are configured' () {
        given:
        def proxy = new FakeProxyServer(USERNAME, PASSWORD)
        and:
        HttpClientFactory.setProxyConfig(ProxyConfig.resolve("127.0.0.1:${proxy.port}"))

        when:
        def client = POLICY == 'follow' ? HttpClientFactory.followRedirectsHttpClient() : HttpClientFactory.neverRedirectsHttpClient()
        def response = client.send(getRequest("http://${targetHost}:${httpTarget.address.port}/hello"), HttpResponse.BodyHandlers.ofString())
        then:
        response.statusCode() == 407
        proxy.rejected.get() >= 1
        proxy.authorized.get() == 0

        cleanup:
        proxy.close()

        where:
        POLICY << ['follow', 'never']
    }

    def 'should authenticate the #POLICY redirects client against the proxy when credentials are configured' () {
        given:
        def proxy = new FakeProxyServer(USERNAME, PASSWORD)
        and:
        HttpClientFactory.setProxyConfig(ProxyConfig.resolve("http://${USERNAME}:${PASSWORD}@127.0.0.1:${proxy.port}"))

        when:
        def client = POLICY == 'follow' ? HttpClientFactory.followRedirectsHttpClient() : HttpClientFactory.neverRedirectsHttpClient()
        def response = client.send(getRequest("http://${targetHost}:${httpTarget.address.port}/hello"), HttpResponse.BodyHandlers.ofString())
        then:
        response.statusCode() == 200
        response.body() == 'Hello from proxy!'
        proxy.authorized.get() >= 1

        cleanup:
        proxy.close()

        where:
        POLICY << ['follow', 'never']
    }

    def 'should bypass the proxy for hosts matching the no-proxy list' () {
        given:
        def proxy = new FakeProxyServer(USERNAME, PASSWORD)
        and:
        HttpClientFactory.setProxyConfig(ProxyConfig.resolve("http://${USERNAME}:${PASSWORD}@127.0.0.1:${proxy.port}", null, null, targetHost))

        when:
        def client = HttpClientFactory.followRedirectsHttpClient()
        def response = client.send(getRequest("http://${targetHost}:${httpTarget.address.port}/hello"), HttpResponse.BodyHandlers.ofString())
        then:
        response.statusCode() == 200
        response.body() == 'Hello world!'
        and: 'the request was made directly, not via the proxy'
        proxy.authorized.get() == 0
        proxy.rejected.get() == 0

        cleanup:
        proxy.close()
    }

    def 'should authenticate the https CONNECT tunnel when credentials are configured' () {
        given:
        def proxy = new FakeProxyServer(USERNAME, PASSWORD)
        and:
        def client = newTlsClient(ProxyConfig.resolve("http://${USERNAME}:${PASSWORD}@127.0.0.1:${proxy.port}"))

        when:
        def response = client.send(getRequest("https://${targetHost}:${httpsTarget.address.port}/hello"), HttpResponse.BodyHandlers.ofString())
        then:
        response.statusCode() == 200
        response.body() == 'Hello secure world!'
        and: 'the request was tunnelled via an authenticated CONNECT request'
        proxy.connectRequests.size() >= 1
        proxy.authorized.get() >= 1

        cleanup:
        proxy.close()
    }

    def 'should fail the https CONNECT tunnel when the proxy requires authentication and no credentials are configured' () {
        given:
        def proxy = new FakeProxyServer(USERNAME, PASSWORD)
        and:
        def client = newTlsClient(ProxyConfig.resolve("127.0.0.1:${proxy.port}"))

        when:
        def response = client.send(getRequest("https://${targetHost}:${httpsTarget.address.port}/hello"), HttpResponse.BodyHandlers.ofString())
        then: 'the proxy 407 response is surfaced and no tunnel is established'
        response.statusCode() == 407
        proxy.rejected.get() >= 1
        proxy.connectRequests.size() == 0

        cleanup:
        proxy.close()
    }
}
