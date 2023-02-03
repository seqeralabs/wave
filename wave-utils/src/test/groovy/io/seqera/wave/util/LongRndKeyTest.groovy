package io.seqera.wave.util

import spock.lang.Ignore
import spock.lang.Specification
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

    @Ignore
    def 'should be unique' () {
        when:
        def map = new HashMap<String,Boolean>()
        def int c=0
        for( int i=0; i<1_000_000; i++ ) {
            if( i % 100_000 == 0 ) println "Keys ${c++}"
            def key = LongRndKey.rndHex()
            if( map.containsKey(key))
                throw new IllegalArgumentException("Key $key already exists")
            map.put(key, true)
        }
        
        then:
        true
    }
}
