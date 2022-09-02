package io.seqera.wave.core

import io.seqera.wave.exception.BadRequestException

/**
 * Core container tools
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class Container {

    private static List<String> ALLOWED_PLATFORMS = ['x86_64', 'amd64', 'arm64']
    public static final String DEFAULT_PLATFORM = 'amd64'

    static String platform(String value) {
        if( !value )
            return DEFAULT_PLATFORM
        if( value !in ALLOWED_PLATFORMS) throw new BadRequestException("Unsupported container platform: $value")
        return value == 'x86_64' ? 'amd64' : value
    }

}
