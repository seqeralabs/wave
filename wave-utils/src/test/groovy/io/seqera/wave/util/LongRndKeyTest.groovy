package io.seqera.wave.util

import spock.lang.Specification

import io.seqera.wave.util.LongRndKey
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class LongRndKeyTest extends Specification {

    def 'should be greater than or equal to zero' () {
        expect:
        10_0000 .times {assert LongRndKey.rndLong() > 0 }
    }

    def 'should return random hex' () {
        expect:
        10_0000 .times {assert LongRndKey.rndHex().size() == 12 }
    }
}
