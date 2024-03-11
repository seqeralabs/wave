/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
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

    @Value('${wave.httpclient.streamThreshold:65536}')
    private int streamThreshold

    @PostConstruct
    private void init() {
        log.info "Http client config: connectTimeout=$connectTimeout; retryAttempts=$retryAttempts; retryDelay=$retryDelay; retryMaxDelay=$retryMaxDelay; retryMultiplier=$retryMultiplier; streamThreshold=$streamThreshold"
    }

    Duration getDelay() { retryDelay }

    Duration getMaxDelay() { retryMaxDelay }

    int getMaxAttempts() { retryAttempts }

    double getJitter() { retryJitter }

    int getStreamThreshold() { streamThreshold }
}
