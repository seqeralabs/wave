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

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.util.StringUtils
/**
 * Model the configuration of an (authenticating) HTTP egress proxy used
 * by {@link HttpClientFactory} to create {@link java.net.http.HttpClient} instances
 *
 * The proxy can be specified either via the {@code wave.httpclient.proxy.*} configuration
 * settings or the {@code HTTPS_PROXY}/{@code HTTP_PROXY}/{@code NO_PROXY} environment variables
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class HttpProxyConfig {

    final String host

    final int port

    final String username

    final String password

    final List<String> noProxy

    // no-proxy entries pre-compiled at construction time since `shouldBypass`
    // runs on every outbound request via the proxy selector
    private final boolean proxyLoopback
    private final boolean bypassAll
    private final Set<String> exactHosts
    private final List<String> suffixPatterns
    private final List<Cidr> cidrPatterns

    protected HttpProxyConfig(String host, int port, String username, String password, List<String> noProxy) {
        this.host = host
        this.port = port
        this.username = username
        this.password = password
        this.noProxy = noProxy ?: List.<String>of()
        this.proxyLoopback = isLoopback(host.toLowerCase())
        this.exactHosts = new HashSet<>()
        this.suffixPatterns = new ArrayList<>()
        this.cidrPatterns = new ArrayList<>()
        boolean all = false
        for( String pattern : this.noProxy ) {
            if( pattern == '*' ) {
                all = true
            }
            else if( pattern.contains('/') ) {
                final cidr = Cidr.parse(pattern)
                if( cidr != null )
                    cidrPatterns.add(cidr)
            }
            else {
                // a domain suffix e.g. `.example.com` or `*.example.com` matches
                // any sub-domain as well as the bare domain itself
                final suffix = pattern.startsWith('*.') ? pattern.substring(1) : pattern
                if( suffix.startsWith('.') ) {
                    suffixPatterns.add(suffix)
                    exactHosts.add(suffix.substring(1))
                }
                else
                    exactHosts.add(suffix)
            }
        }
        this.bypassAll = all
    }

    /**
     * Parse a proxy URI in the form {@code [http[s]://][user:password@]host[:port]}
     *
     * @param uri The proxy URI string
     * @param username The proxy username; when provided it takes precedence over the URI user-info
     * @param password The proxy password; when provided it takes precedence over the URI user-info
     * @param noProxy Comma separated list of hosts that should bypass the proxy
     * @return The corresponding {@link HttpProxyConfig} object or {@code null} when the URI is empty
     */
    static HttpProxyConfig parse(String uri, String username=null, String password=null, String noProxy=null) {
        if( !uri )
            return null
        final URI parsed
        try {
            parsed = new URI(uri.contains('://') ? uri : 'http://' + uri)
        }
        catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid proxy URI - offending value: '$uri'", e)
        }
        if( !parsed.host )
            throw new IllegalArgumentException("Invalid proxy URI - missing host name - offending value: '$uri'")
        final port = parsed.port > 0
                ? parsed.port
                : (parsed.scheme == 'https' ? 443 : 80)
        String user = username
        String pass = password
        if( !user && parsed.userInfo ) {
            final p = parsed.userInfo.indexOf(':')
            user = decode(p >= 0 ? parsed.userInfo.substring(0, p) : parsed.userInfo)
            if( p >= 0 )
                pass = decode(parsed.userInfo.substring(p + 1))
        }
        return new HttpProxyConfig(parsed.host, port, user, pass, splitNoProxy(noProxy))
    }

    /**
     * Create the proxy configuration from the {@code HTTPS_PROXY}/{@code HTTP_PROXY} and
     * {@code NO_PROXY} environment variables (upper and lower case variants are supported)
     *
     * @param env The environment map, defaults to {@link System#getenv()}
     * @return The corresponding {@link HttpProxyConfig} or {@code null} when no proxy is defined
     */
    static HttpProxyConfig fromEnvironment(Map<String,String> env = System.getenv()) {
        final uri = env.get('HTTPS_PROXY') ?: env.get('https_proxy') ?: env.get('HTTP_PROXY') ?: env.get('http_proxy')
        if( !uri )
            return null
        final noProxy = env.get('NO_PROXY') ?: env.get('no_proxy')
        return parse(uri, null, null, noProxy)
    }

    static private String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8)
    }

    static private List<String> splitNoProxy(String noProxy) {
        return noProxy
                ? noProxy.tokenize(',').collect(it -> it.trim().toLowerCase()).findAll(it -> it.size()>0)
                : List.<String>of()
    }

    /**
     * @return A {@link ProxySelector} routing all requests via this proxy, except the
     * hosts matching the no-proxy list
     */
    ProxySelector proxySelector() {
        final proxied = List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)))
        final direct = List.of(Proxy.NO_PROXY)
        return new ProxySelector() {
            @Override
            List<Proxy> select(URI uri) {
                return shouldBypass(uri.host) ? direct : proxied
            }
            @Override
            void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                log.warn "Unable to connect proxy ${sa} for request ${uri} - cause: ${ioe.message}"
            }
        }
    }

    /**
     * @return An {@link Authenticator} providing the proxy credentials, restricted to
     * proxy authentication requests originating from this proxy host and port, or
     * {@code null} when no credentials are defined
     */
    Authenticator authenticator() {
        if( !username )
            return null
        final auth = new PasswordAuthentication(username, (password ?: '').toCharArray())
        final proxyHost = host
        final proxyPort = port
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if( getRequestorType() == Authenticator.RequestorType.PROXY
                        && proxyHost.equalsIgnoreCase(getRequestingHost())
                        && proxyPort == getRequestingPort() )
                    return auth
                return null
            }
        }
    }

    /**
     * Determine whether a target host should bypass the proxy. Entries in the no-proxy list
     * can be exact host names, domain suffixes e.g. {@code .example.com} or {@code *.example.com},
     * IPv4 CIDR blocks e.g. {@code 10.0.0.0/8} or the {@code *} wildcard. Loopback addresses
     * always bypass the proxy, unless the proxy itself is a loopback address
     *
     * @param targetHost The host name of the request to be evaluated
     * @return {@code true} when the request should be made directly, {@code false} when it should go via the proxy
     */
    boolean shouldBypass(String targetHost) {
        if( !targetHost )
            return false
        final target = targetHost.toLowerCase()
        if( isLoopback(target) && !proxyLoopback )
            return true
        if( bypassAll )
            return true
        if( exactHosts.contains(target) )
            return true
        for( String suffix : suffixPatterns ) {
            if( target.endsWith(suffix) )
                return true
        }
        if( cidrPatterns ) {
            final address = ipv4ToLong(target)
            if( address >= 0 ) {
                for( Cidr cidr : cidrPatterns ) {
                    if( cidr.matches(address) )
                        return true
                }
            }
        }
        return false
    }

    static private boolean isLoopback(String host) {
        return host == 'localhost' || host.startsWith('127.') || host == '::1' || host == '[::1]'
    }

    /**
     * Model an IPv4 CIDR block e.g. {@code 10.0.0.0/8} as a masked base address
     */
    static private class Cidr {
        final long base
        final long mask

        private Cidr(long base, long mask) {
            this.base = base
            this.mask = mask
        }

        boolean matches(long address) {
            return (address & mask) == base
        }

        static Cidr parse(String cidr) {
            final p = cidr.indexOf('/')
            final address = ipv4ToLong(cidr.substring(0, p))
            final bits = cidr.substring(p + 1)
            if( address < 0 || !bits.isInteger() )
                return null
            final len = bits.toInteger()
            if( len < 0 || len > 32 )
                return null
            final mask = len == 0 ? 0L : (0xFFFFFFFFL << (32 - len)) & 0xFFFFFFFFL
            return new Cidr(address & mask, mask)
        }
    }

    static private long ipv4ToLong(String address) {
        final parts = address.tokenize('.')
        if( parts.size() != 4 )
            return -1
        long result = 0
        for( String it : parts ) {
            if( !it.isInteger() )
                return -1
            final octet = it.toInteger()
            if( octet < 0 || octet > 255 )
                return -1
            result = (result << 8) | octet
        }
        return result
    }

    @Override
    String toString() {
        return "HttpProxyConfig[host=$host; port=$port; username=${username ?: '-'}; password=${StringUtils.redact(password)}; noProxy=${noProxy.join(',') ?: '-'}]"
    }
}
