package io.seqera.wave.configuration

import java.time.Duration

import groovy.transform.CompileStatic


/**
 * A simple bean to configure a max items per duration
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@CompileStatic
class LimitConfig {
    int max
    Duration duration
}
