/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.configuration

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.util.Retryable
import jakarta.inject.Singleton
/**
 * Model  Http Client settings
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Singleton
@Slf4j
class HttpClientConfig implements Retryable.Config {

    @Value('${wave.httpclient.connectTimeout:20s}')
    Duration connectTimeout

    @Value('${wave.httpclient.retry.delay:1s}')
    Duration retryDelay

    @Value('${wave.httpclient.retry.maxDelay}')
    @Nullable
    Duration retryMaxDelay

    @Value('${wave.httpclient.retry.attempts:3}')
    int retryAttempts

    @Value('${wave.httpclient.retry.multiplier:1.0}')
    float retryMultiplier

    @Value('${wave.httpclient.retry.jitter:0.25}')
    double retryJitter

    @Value('${wave.httpclient.virtualThreadsPool:false}')
    boolean virtualThreadsPool

    @PostConstruct
    private void init() {
        log.info "Http client config: virtualThreads=${virtualThreadsPool}; connectTimeout=$connectTimeout; retryAttempts=$retryAttempts; retryDelay=$retryDelay; retryMaxDelay=$retryMaxDelay; retryMultiplier=$retryMultiplier"
    }

    Duration getDelay() { retryDelay }

    Duration getMaxDelay() { retryMaxDelay }

    int getMaxAttempts() { retryAttempts }

    double getJitter() { retryJitter }

    boolean virtualThreadsPool() { virtualThreadsPool }
}
