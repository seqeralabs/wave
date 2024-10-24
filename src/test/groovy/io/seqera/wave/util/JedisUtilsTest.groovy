package io.seqera.wave.util

import spock.lang.Specification

import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Protocol

class JedisUtilsTest extends Specification {

    def 'should build address' () {
        expect:
        JedisUtils.buildHostAndPort(ADDR) == EXPECTED

        where:
        ADDR                    | EXPECTED
        'foo'                   | new HostAndPort('foo', 6379)
        'bar.com:5000'          | new HostAndPort('bar.com', 5000)
        'redis://bar.com:1234'  | new HostAndPort('bar.com', 1234)
        'rediss://bar.com:1234' | new HostAndPort('bar.com', 1234)
    }

    def 'should report and error' () {
        when:
        JedisUtils.buildHostAndPort('http://foo')
        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Invalid Redis address: 'http://foo' - it should match the regex (rediss?://)?(?<host>[^:]+)(:(?<port>\\d+))?"
    }

    def 'should build basic client config' () {
        when:
        def config = JedisUtils.buildClientConfig('redis://foo', null, null)
        then:
        !config.ssl
        !config.password
        config.socketTimeoutMillis == Protocol.DEFAULT_TIMEOUT
        config.connectionTimeoutMillis == Protocol.DEFAULT_TIMEOUT
    }

    def 'should build client config with ssl' () {
        when:
        def config = JedisUtils.buildClientConfig('rediss://foo', 'password', 1000)
        then:
        config.ssl
        config.password == 'password'
        config.socketTimeoutMillis == 1000
        config.connectionTimeoutMillis == 1000
    }
}

