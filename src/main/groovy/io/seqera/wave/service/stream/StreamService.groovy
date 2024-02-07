package io.seqera.wave.service.stream

/**
 * Define the interface for remote resource steaming
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface StreamService {

    InputStream stream(String location)

}

