package io.seqera.wave.encoder

/**
 * Define JSON encode-decode core operations
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface EncodingStrategy<V> {

    String encode(V value)

    V decode(String value)

}
