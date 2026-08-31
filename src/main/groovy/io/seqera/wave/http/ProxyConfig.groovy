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

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.util.logging.Slf4j
import io.seqera.http.HxProxyConfig
import io.seqera.wave.util.StringUtils
/**
 * Model the configuration of an (authenticating) HTTP egress proxy, resolved either from
 * the {@code wave.httpclient.proxy.*} settings or the {@code HTTPS_PROXY}/{@code HTTP_PROXY}/
 * {@code NO_PROXY} environment variables, into the {@link HxProxyConfig} applied by
 * {@link HttpClientFactory} to the {@link java.net.http.HttpClient} instances it creates.
 *
 * This mirrors the proxy handling implemented by Nextflow via {@code nextflow.util.ProxyConfig}
 * on top of the same {@code io.seqera:lib-httpx} library
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@EqualsAndHashCode
@CompileStatic
class ProxyConfig {

    String protocol
    String host
    String port
    String username
    String password

    @Override
    String toString() {
        def result = protocol ? "protocol=$protocol; host=$host" : "host=$host"
        if( port ) result += "; port=$port"
        if( username ) result += "; username=$username"
        if( password ) result += "; password=${StringUtils.redact(password)}"
        return "ProxyConfig[$result]"
    }

    /**
     * Parse a proxy URL string retrieving the host, port, username and password components
     *
     * @param value A proxy string e.g. {@code hostname}, {@code hostname:port}, {@code http://hostname:port},
     *      {@code http://username:password@hostname:port}
     * @return The corresponding {@link ProxyConfig} or {@code null} when the value is empty
     *
     * @throws MalformedURLException when the specified value is not a valid proxy url
     */
    static ProxyConfig parse(final String value) {
        if( !value )
            return null

        final result = new ProxyConfig()
        int p

        if( value.contains('://') ) {
            def url = new URL(value)
            result.host = url.host
            result.protocol = url.protocol
            if( url.port > 0 )
                result.port = url.port as String
            if( (p=url.userInfo?.indexOf(':') ?: -1) != -1 ) {
                result.username = decodeUserInfo(url.userInfo.substring(0,p))
                result.password = decodeUserInfo(url.userInfo.substring(p+1))
            }
        }
        else if( (p=value.indexOf(':')) != -1 ) {
            result.host = value.substring(0,p)
            result.port = value.substring(p+1)
        }
        else {
            result.host = value
        }

        return result
    }

    /**
     * Resolve the {@link HxProxyConfig} from an explicit proxy URI. The proxy is applied
     * to both plain HTTP and HTTPS destinations
     *
     * @param uri The proxy URI e.g. {@code http://username:password@hostname:port}
     * @param username The proxy username; when provided it takes precedence over the URI user-info
     * @param password The proxy password; when provided it takes precedence over the URI user-info
     * @param noProxy Comma separated list of hosts that should bypass the proxy
     * @return The corresponding {@link HxProxyConfig} or {@code null} when the URI is empty
     */
    static HxProxyConfig resolve(String uri, String username=null, String password=null, String noProxy=null) {
        final proxy = parse(uri)
        if( proxy == null )
            return null
        if( username ) {
            proxy.username = username
            proxy.password = password
        }
        log.info "Http client proxy settings: $proxy; noProxy=${noProxy ?: '-'}"
        final port = portAsInt(proxy.port, 'https'.equalsIgnoreCase(proxy.protocol) ? 443 : 80)
        return HxProxyConfig.newBuilder()
                .httpProxy(proxy.host, port, proxy.username, proxy.password)
                .httpsProxy(proxy.host, port, proxy.username, proxy.password)
                .noProxy(noProxy?.tokenize(','))
                .build()
    }

    /**
     * Resolve the {@link HxProxyConfig} from the {@code HTTPS_PROXY}/{@code HTTP_PROXY} and
     * {@code NO_PROXY} environment variables (upper and lower case variants are supported)
     *
     * @param env The environment map, defaults to {@link System#getenv()}
     * @return The corresponding {@link HxProxyConfig} or {@code null} when no proxy is defined
     */
    static HxProxyConfig fromEnvironment(Map<String,String> env = System.getenv()) {
        final http = parse(env.get('HTTP_PROXY') ?: env.get('http_proxy'))
        final https = parse(env.get('HTTPS_PROXY') ?: env.get('https_proxy'))
        if( !http && !https )
            return null
        final noProxy = env.get('NO_PROXY') ?: env.get('no_proxy')
        log.info "Http client proxy settings from environment: http=${http ?: '-'}; https=${https ?: '-'}; noProxy=${noProxy ?: '-'}"
        final builder = HxProxyConfig.newBuilder()
        if( http )
            builder.httpProxy(http.host, portAsInt(http.port, 80), http.username, http.password)
        if( https )
            builder.httpsProxy(https.host, portAsInt(https.port, 443), https.username, https.password)
        builder.noProxy(noProxy?.tokenize(','))
        return builder.build()
    }

    /**
     * Percent-decode a userinfo component (username or password) per RFC 3986, so proxy
     * credentials carrying special characters (e.g. {@code @}, {@code :}) work. A literal
     * {@code +} is preserved — userinfo is not form-encoded — so it is shielded from the
     * {@code +}→space rule of {@link URLDecoder}.
     */
    static private String decodeUserInfo(String s) {
        return s != null ? URLDecoder.decode(s.replace('+', '%2B'), 'UTF-8') : null
    }

    static private int portAsInt(String port, int defaultPort) {
        if( !port )
            return defaultPort
        try {
            return Integer.parseInt(port.trim())
        }
        catch( NumberFormatException e ) {
            log.warn("Ignoring invalid proxy port '$port' - using default $defaultPort")
            return defaultPort
        }
    }
}
