package io.seqera.wave.api

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class FusionVersionTest extends Specification {

    def 'should parse version from uri' () {
        expect:
        FusionVersion.from(STR) == EXPECTED

        where:
        EXPECTED                            | STR
        null                                | null
        new FusionVersion('2','amd64')      | 'https://foo.com/v2-amd64.json'
        new FusionVersion('22','amd64')     | 'https://foo.com/v22-amd64.json'
        new FusionVersion('2.1','amd64')    | 'https://foo.com/v2.1-amd64.json'
        new FusionVersion('2.11','amd64')   | 'https://foo.com/v2.11-amd64.json'
        new FusionVersion('2.1.3','amd64')  | 'https://foo.com/v2.1.3-amd64.json'
        new FusionVersion('2.1.3','arm64')  | 'https://foo.com/v2.1.3-arm64.json'
        new FusionVersion('2.1.33','arm64') | 'https://foo.com/v2.1.33-arm64.json'
        new FusionVersion('2.1.3a','arm64') | 'https://foo.com/v2.1.3a-arm64.json'
        new FusionVersion('2.1.3.a','arm64')| 'https://foo.com/v2.1.3.a-arm64.json'

    }

}
