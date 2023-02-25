package io.seqera.wave.service.validation

import spock.lang.Specification
import spock.lang.Unroll

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = 'prod')
class ValidationServiceProdTest extends Specification {

    @Inject
    ValidationService validationService

    @Unroll
    void 'checks whether several hostnames are valid or not'() {
        expect:
        ValidationServiceProd.isValidHostname(address) == isValid

        where:
        address                                   | isValid
        '10.0.0.0'                                | false
        '010.0.0.0'                               | false
        '010.167.42.4'                            | false
        '10.255.255.255'                          | false
        '172.16.0.0'                              | false
        '172.16.25.255'                           | false
        '172.31.255.255'                          | false
        '192.168.0.0'                             | false
        '192.168.255.255'                         | false
        '127.0.0.0'                               | false
        '127.255.255.255'                         | false
        'fdff:1234:abcd:5678:effe:9098:dcba:7654' | false
        'fc00:1234:abcd:5678:effe:9098:dcba:7654' | false
        'localhost'                               | false
        ' localhost '                             | false
        'LoCaLhOsT'                               | false
        'com'                                     | false
        'google.com'                              | true
        '81.40.42.206'                            | true
        '081.040.042.006'                         | true
        '2001:0db8:85a3:0000:0000:8a2e:0370:7334' | true
    }

    @Unroll
    def 'should check valid endpoint' () {
        expect:
        validationService.checkEndpoint(ENDPOINT)==EXPECTED

        where:
        ENDPOINT                | EXPECTED
        'foo'                   | "Missing endpoint protocol — offending value: foo"
        'ftp://foo.com'         | "Invalid endpoint protocol — offending value: ftp://foo.com"
        'http://a b c'          | "Invalid endpoint 'http://a b c' — cause: Illegal character in authority at index 7: http://a b c"
        'http://localhost'      | 'Endpoint hostname not allowed — offending value: http://localhost'
        'http://localhost:8000' | 'Endpoint hostname not allowed — offending value: http://localhost:8000'
        'http://10.0.0.0/api'   | 'Endpoint hostname not allowed — offending value: http://10.0.0.0/api'
        and:
        'http://foo.com'        | null
        'https://foo.com/api'   | null
        'https://a.b.c/api'     | null
        'https://a.b.c:80/api'  | null
    }

    def 'should check container name' () {
        expect:
        validationService.checkContainerName('ubuntu:latest') == null
    }
}
