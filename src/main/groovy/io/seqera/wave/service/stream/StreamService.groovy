package io.seqera.wave.service.stream

import io.seqera.wave.tower.PlatformId

/**
 * Define the interface for remote resource steaming
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface StreamService {

    InputStream stream(String location, PlatformId identity)

}

