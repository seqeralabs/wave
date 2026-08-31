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

import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class ProxyConfigTest extends Specification {

    static final List<Proxy> DIRECT = List.of(Proxy.NO_PROXY)

    static List<Proxy> proxied(String host, int port) {
        return List.of(new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port)))
    }

    @Unroll
    def 'should parse proxy uri #PROXY_URI' () {
        when:
        def config = ProxyConfig.parse(PROXY_URI)
        then:
        config?.protocol == PROTOCOL
        config?.host == HOST
        config?.port == PORT
        config?.username == USER
        config?.password == PASS

        where:
        PROXY_URI                                   | PROTOCOL  | HOST                  | PORT      | USER      | PASS
        null                                        | null      | null                  | null      | null      | null
        ''                                          | null      | null                  | null      | null      | null
        'proxy.example.com'                         | null      | 'proxy.example.com'   | null      | null      | null
        'proxy.example.com:3128'                    | null      | 'proxy.example.com'   | '3128'    | null      | null
        'http://proxy.example.com'                  | 'http'    | 'proxy.example.com'   | null      | null      | null
        'https://proxy.example.com:8080'            | 'https'   | 'proxy.example.com'   | '8080'    | null      | null
        'http://foo:bar@proxy.example.com:8080'     | 'http'    | 'proxy.example.com'   | '8080'    | 'foo'     | 'bar'
        'http://foo:p%40ss@proxy.example.com'       | 'http'    | 'proxy.example.com'   | null      | 'foo'     | 'p@ss'
        'http://foo:p+ss@proxy.example.com'         | 'http'    | 'proxy.example.com'   | null      | 'foo'     | 'p+ss'
        'http://foo:b:ar@proxy.example.com:1234'    | 'http'    | 'proxy.example.com'   | '1234'    | 'foo'     | 'b:ar'
    }

    def 'should resolve a proxy uri applying it to both http and https destinations' () {
        when:
        def config = ProxyConfig.resolve('http://proxy.example.com:3128')
        def selector = config.toProxySelector()
        then:
        selector.select(new URI('http://quay.io/v2/')) == proxied('proxy.example.com', 3128)
        selector.select(new URI('https://quay.io/v2/')) == proxied('proxy.example.com', 3128)
        and:
        !config.hasCredentials()
        config.toAuthenticator() == null
    }

    @Unroll
    def 'should default the proxy port by protocol for #PROXY_URI' () {
        when:
        def selector = ProxyConfig.resolve(PROXY_URI).toProxySelector()
        then:
        selector.select(new URI('https://quay.io/v2/')) == proxied('proxy.example.com', PORT)

        where:
        PROXY_URI                   | PORT
        'proxy.example.com'         | 80
        'http://proxy.example.com'  | 80
        'https://proxy.example.com' | 443
    }

    def 'should return no proxy config when the uri is empty' () {
        expect:
        ProxyConfig.resolve(null) == null
        ProxyConfig.resolve('') == null
    }

    def 'should give precedence to explicit credentials over uri user-info' () {
        given:
        def config = ProxyConfig.resolve('http://foo:bar@proxy.example.com:8080', 'this', 'that', null)

        when:
        def result = config.toAuthenticator().requestPasswordAuthenticationInstance(
                'proxy.example.com', null, 8080, 'http', 'auth required', 'basic', null, Authenticator.RequestorType.PROXY)
        then:
        result.userName == 'this'
        result.password == 'that'.toCharArray()
    }

    @Unroll
    def 'should resolve proxy from environment #ENV' () {
        when:
        def config = ProxyConfig.fromEnvironment(ENV)
        def selector = config?.toProxySelector()
        then:
        (selector?.select(new URI('http://foo.com/'))) == HTTP_RESULT
        (selector?.select(new URI('https://foo.com/'))) == HTTPS_RESULT

        where:
        ENV                                                             | HTTP_RESULT               | HTTPS_RESULT
        [:]                                                             | null                      | null
        [HTTPS_PROXY: 'http://proxy1:3128']                             | DIRECT                    | proxied('proxy1', 3128)
        [https_proxy: 'http://proxy1:3128']                             | DIRECT                    | proxied('proxy1', 3128)
        [HTTP_PROXY: 'http://proxy2:8080']                              | proxied('proxy2', 8080)   | DIRECT
        [HTTPS_PROXY: 'http://proxy1:3128', HTTP_PROXY: 'http://x:1']   | proxied('x', 1)           | proxied('proxy1', 3128)
        [HTTPS_PROXY: 'https://proxy1']                                 | DIRECT                    | proxied('proxy1', 443)
    }

    def 'should resolve proxy credentials and no-proxy from environment' () {
        when:
        def config = ProxyConfig.fromEnvironment([HTTPS_PROXY: 'http://foo:bar@proxy1:3128', NO_PROXY: 'a.com'])
        then:
        config.hasCredentials()
        config.toProxySelector().select(new URI('https://a.com/')) == DIRECT
        and:
        def result = config.toAuthenticator().requestPasswordAuthenticationInstance(
                'proxy1', null, 3128, 'http', 'auth required', 'basic', null, Authenticator.RequestorType.PROXY)
        result.userName == 'foo'
        result.password == 'bar'.toCharArray()
    }

    @Unroll
    def 'should bypass=#EXPECTED the proxy for host #TARGET with no-proxy #NO_PROXY' () {
        given:
        def selector = ProxyConfig.resolve('proxy.example.com:3128', null, null, NO_PROXY).toProxySelector()
        expect:
        (selector.select(new URI("https://${TARGET}/")) == DIRECT) == EXPECTED

        where:
        TARGET              | NO_PROXY                      | EXPECTED
        'quay.io'           | null                          | false
        'quay.io'           | 'docker.io'                   | false
        'docker.io'         | 'docker.io'                   | true
        'DOCKER.IO'         | 'docker.io'                   | true
        // a bare host name entry also matches its sub-domains
        'registry.docker.io'| 'docker.io'                   | true
        // a `.` or `*.` suffix entry matches sub-domains only
        'reg.example.com'   | '.example.com'                | true
        'reg.example.com'   | '*.example.com'               | true
        'example.com'       | '.example.com'                | false
        'notexample.com'    | '.example.com'                | false
        'anything.io'       | '*'                           | true
        // loopback addresses always bypass the proxy
        'localhost'         | null                          | true
        '127.0.0.1'         | null                          | true
    }

    def 'should create authenticator scoped to the proxy host and requestor type' () {
        given:
        def auth = ProxyConfig.resolve('http://foo:bar@proxy.example.com:3128').toAuthenticator()

        when: 'the proxy asks for authentication'
        def result = auth.requestPasswordAuthenticationInstance('proxy.example.com', null, 3128, 'http', 'auth required', 'basic', null, Authenticator.RequestorType.PROXY)
        then:
        result.userName == 'foo'
        result.password == 'bar'.toCharArray()

        when: 'a server (not the proxy) asks for authentication'
        result = auth.requestPasswordAuthenticationInstance('proxy.example.com', null, 3128, 'http', 'auth required', 'basic', null, Authenticator.RequestorType.SERVER)
        then:
        result == null

        when: 'a different host asks for proxy authentication'
        result = auth.requestPasswordAuthenticationInstance('other.example.com', null, 3128, 'http', 'auth required', 'basic', null, Authenticator.RequestorType.PROXY)
        then:
        result == null

        when: 'a different port asks for proxy authentication'
        result = auth.requestPasswordAuthenticationInstance('proxy.example.com', null, 8080, 'http', 'auth required', 'basic', null, Authenticator.RequestorType.PROXY)
        then:
        result == null
    }

    def 'should redact password in string representation' () {
        expect:
        !ProxyConfig.parse('http://foo:secret1234@proxy.example.com').toString().contains('secret1234')
    }
}
