package io.seqera.wave.util

import spock.lang.Specification

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class DigestFunctionsTest extends Specification {

    def 'should return consistent md5 hash'() {
        expect:
        DigestFunctions.md5((String)null) == '93b885adfe0da089cdf634904fd59f71'
        DigestFunctions.md5('') == 'd41d8cd98f00b204e9800998ecf8427e'
        DigestFunctions.md5('FROM quay.io/nextflow/bash') == 'd29a903fececc3c0448c4b3bf31f0be0'
    }

    def str(int x) { Character.toString(x) }

    def 'should return consistent md5 hash from map'() {
        expect:
        DigestFunctions.md5([foo: null])
                == DigestFunctions.md5((new StringBuilder() << 'foo' << str(0x1C) << str(0x0) << str(0x1E)) .toString())

        and:
        DigestFunctions.md5([foo: ''])
                == DigestFunctions.md5((new StringBuilder() << 'foo' << str(0x1C) << '' << str(0x1E)) .toString())

        and:
        DigestFunctions.md5([foo: 'hello world'])
                == DigestFunctions.md5((new StringBuilder() << 'foo' << str(0x1C) << 'hello world' << str(0x1E)) .toString())

        and:
        DigestFunctions.md5([foo: 'hello world', bar: ['one','two','three']])
                == DigestFunctions.md5((new StringBuilder() << 'foo' << str(0x1C) << 'hello world' << str(0x1E) << ('bar' << str(0x1C) << ('one' << str(0x1D) << 'two' << str(0x1D) <<'three') << str(0x1E))) .toString())

        when:
        DigestFunctions.md5((Map)null)
        then:
        thrown(IllegalArgumentException)

        when:
        DigestFunctions.md5([:])
        then:
        thrown(IllegalArgumentException)
    }

}
