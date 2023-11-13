package io.seqera.wave.filter

/**
 * Define the order of HTTP filters. The smaller number has higher priority
 *
 * {@link io.micronaut.core.order.Ordered}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FilterOrder {

    final int DENY_PATHS = -100
    final int RATE_LIMITER = -50

}
