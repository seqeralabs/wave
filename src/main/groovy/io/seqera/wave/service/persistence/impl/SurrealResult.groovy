package io.seqera.wave.service.persistence.impl

/**
 * Model a Surreal Resultset
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SurrealResult<T> {

    String time
    String status
    T[] result

}
