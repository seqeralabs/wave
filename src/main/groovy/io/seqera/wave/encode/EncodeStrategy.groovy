package io.seqera.wave.encode

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface EncodeStrategy<V> {

    String encode(V value)

    V decode(String value)

}
