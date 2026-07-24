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
class HttpProxyConfigTest extends Specification {

    @Unroll
    def 'should parse proxy uri #PROXY_URI' () {
        when:
        def config = HttpProxyConfig.parse(PROXY_URI)
        then:
        config?.host == HOST
        config?.port == PORT
        config?.username == USER
        config?.password == PASS

        where:
        PROXY_URI                               | HOST                  | PORT  | USER      | PASS
        null                                    | null                  | null  | null      | null
        ''                                      | null                  | null  | null      | null
        'proxy.example.com:3128'                | 'proxy.example.com'   | 3128  | null      | null
        'http://proxy.example.com'              | 'proxy.example.com'   | 80    | null      | null
        'https://proxy.example.com'             | 'proxy.example.com'   | 443   | null      | null
        'http://foo:bar@proxy.example.com:8080' | 'proxy.example.com'   | 8080  | 'foo'     | 'bar'
        'http://foo@proxy.example.com:8080'     | 'proxy.example.com'   | 8080  | 'foo'     | null
        'http://foo:p%40ss@proxy.example.com'   | 'proxy.example.com'   | 80    | 'foo'     | 'p@ss'
        'foo:b:ar@proxy.example.com:1234'       | 'proxy.example.com'   | 1234  | 'foo'     | 'b:ar'
    }

    def 'should give precedence to explicit credentials over uri user-info' () {
        when:
        def config = HttpProxyConfig.parse('http://foo:bar@proxy.example.com:8080', 'this', 'that', null)
        then:
        config.username == 'this'
        config.password == 'that'
    }

    def 'should report invalid proxy uri' () {
        when:
        HttpProxyConfig.parse('http://')
        then:
        thrown(IllegalArgumentException)
    }

    def 'should parse no-proxy list' () {
        when:
        def config = HttpProxyConfig.parse('proxy.example.com:3128', null, null, 'foo.com , .Bar.com,, 10.0.0.0/8')
        then:
        config.noProxy == ['foo.com', '.bar.com', '10.0.0.0/8']
    }

    @Unroll
    def 'should resolve proxy from environment #ENV' () {
        when:
        def config = HttpProxyConfig.fromEnvironment(ENV)
        then:
        config?.host == HOST
        config?.port == PORT
        config?.username == USER
        config?.noProxy == NO_PROXY

        where:
        ENV                                                             | HOST          | PORT  | USER  | NO_PROXY
        [:]                                                             | null          | null  | null  | null
        [HTTPS_PROXY: 'http://proxy1:3128']                             | 'proxy1'      | 3128  | null  | []
        [https_proxy: 'http://proxy1:3128']                             | 'proxy1'      | 3128  | null  | []
        [HTTP_PROXY: 'http://proxy2:8080']                              | 'proxy2'      | 8080  | null  | []
        [HTTPS_PROXY: 'http://proxy1:3128', HTTP_PROXY: 'http://x:1']   | 'proxy1'      | 3128  | null  | []
        [HTTPS_PROXY: 'http://foo:bar@proxy1:3128', NO_PROXY: 'a.com']  | 'proxy1'      | 3128  | 'foo' | ['a.com']
    }

    @Unroll
    def 'should bypass=#EXPECTED proxy for host #TARGET with no-proxy #NO_PROXY' () {
        given:
        def config = HttpProxyConfig.parse('proxy.example.com:3128', null, null, NO_PROXY)
        expect:
        config.shouldBypass(TARGET) == EXPECTED

        where:
        TARGET              | NO_PROXY                      | EXPECTED
        'quay.io'           | null                          | false
        'quay.io'           | 'docker.io'                   | false
        'docker.io'         | 'docker.io'                   | true
        'DOCKER.IO'         | 'docker.io'                   | true
        'reg.example.com'   | '.example.com'                | true
        'example.com'       | '.example.com'                | true
        'reg.example.com'   | '*.example.com'               | true
        'notexample.com'    | '.example.com'                | false
        'anything.io'       | '*'                           | true
        '10.1.2.3'          | '10.0.0.0/8'                  | true
        '11.1.2.3'          | '10.0.0.0/8'                  | false
        '192.168.1.10'      | '10.0.0.0/8,192.168.0.0/16'   | true
        // loopback addresses are never proxied when the proxy is a remote host
        'localhost'         | null                          | true
        '127.0.0.1'         | null                          | true
        '::1'               | null                          | true
    }

    def 'should not bypass loopback host when the proxy itself is a loopback address' () {
        given:
        def config = HttpProxyConfig.parse('127.0.0.1:3128')
        expect:
        !config.shouldBypass('localhost')
        !config.shouldBypass('127.0.0.1')
    }

    def 'should select proxy honouring no-proxy list' () {
        given:
        def config = HttpProxyConfig.parse('proxy.example.com:3128', null, null, 'internal.example.com')
        def selector = config.proxySelector()

        when:
        def result = selector.select(new URI('https://quay.io/v2/'))
        then:
        result.size() == 1
        result[0].type() == Proxy.Type.HTTP
        result[0].address() == InetSocketAddress.createUnresolved('proxy.example.com', 3128)

        when:
        result = selector.select(new URI('https://internal.example.com/v2/'))
        then:
        result == [Proxy.NO_PROXY]
    }

    def 'should create authenticator scoped to the proxy host and requestor type' () {
        given:
        def config = HttpProxyConfig.parse('http://foo:bar@proxy.example.com:3128')
        def auth = config.authenticator()

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
    }

    def 'should not create authenticator when no credentials are given' () {
        expect:
        HttpProxyConfig.parse('proxy.example.com:3128').authenticator() == null
    }

    def 'should redact password in string representation' () {
        expect:
        !HttpProxyConfig.parse('http://foo:secret1234@proxy.example.com').toString().contains('secret1234')
    }
}
