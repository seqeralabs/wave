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

package io.seqera.wave.service.aws

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.util.retry.Retryable
import jakarta.inject.Singleton

/**
 * Model AWS STS client retry settings
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
@Slf4j
class StsClientConfig implements Retryable.Config {

    @Value('${wave.aws.sts.retry.delay:1s}')
    Duration retryDelay

    @Value('${wave.aws.sts.retry.maxDelay:10s}')
    @Nullable
    Duration retryMaxDelay

    @Value('${wave.aws.sts.retry.attempts:3}')
    int retryAttempts

    @Value('${wave.aws.sts.retry.multiplier:2.0}')
    double retryMultiplier

    @Value('${wave.aws.sts.retry.jitter:0.25}')
    double retryJitter

    @PostConstruct
    private void init() {
        log.info "STS client config: retryAttempts=$retryAttempts; retryDelay=$retryDelay; retryMaxDelay=$retryMaxDelay; retryMultiplier=$retryMultiplier; retryJitter=$retryJitter"
    }

    Duration getDelay() { retryDelay }

    Duration getMaxDelay() { retryMaxDelay }

    int getMaxAttempts() { retryAttempts }

    double getJitter() { retryJitter }

    double getMultiplier() { retryMultiplier }
}