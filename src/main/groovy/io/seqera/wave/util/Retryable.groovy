/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

package io.seqera.wave.util

import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.function.Consumer
import java.util.function.Predicate

import dev.failsafe.Failsafe
import dev.failsafe.RetryPolicy
import dev.failsafe.event.EventListener
import dev.failsafe.event.ExecutionAttemptedEvent
import dev.failsafe.function.CheckedSupplier
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j

/**
 * Implements a retry strategy based on Fail safe
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Slf4j
class Retryable {

    interface Config {
        Duration getDelay()
        Duration getMaxDelay()
        int getMaxAttempts()
        double getJitter()
    }

    static final private Duration DEFAULT_DELAY = Duration.ofMillis(500)
    static final private Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(30)
    static final private int DEFAULT_MAX_ATTEMPTS = 5
    static final private double DEFAULT_JITTER = 0.25d
    static final private Predicate<? extends Throwable> DEFAULT_CONDITION = (e -> e instanceof IOException) as Predicate<? extends Throwable>

    private Config config

    private Predicate<? extends Throwable> condition

    private Consumer<ExecutionAttemptedEvent<?>> retryEvent

    Retryable withConfig(Config config) {
        this.config = config
        return this
    }

    Retryable withCondition(Predicate<? extends Throwable> cond) {
        this.condition = cond
        return this
    }

    Retryable onRetry(Consumer<ExecutionAttemptedEvent<?>> event) {
        this.retryEvent = event
        return this
    }

    protected RetryPolicy retryPolicy() {
        final listener = new EventListener<ExecutionAttemptedEvent>() {
            @Override
            void accept(ExecutionAttemptedEvent event) throws Throwable {
                retryEvent?.accept(event)
            }
        }

        final d = config.delay ?: DEFAULT_DELAY
        final m = config.maxDelay ?: DEFAULT_MAX_DELAY
        final a = config.maxAttempts ?: DEFAULT_MAX_ATTEMPTS
        final j = config.jitter ?: DEFAULT_JITTER
        final c = condition ?: DEFAULT_CONDITION

        return RetryPolicy.builder()
                .handleIf(c)
                .withBackoff(d.toMillis(), m.toMillis(), ChronoUnit.MILLIS)
                .withMaxAttempts(a)
                .withJitter(j)
                .onRetry(listener)
                .build()
    }

    <T> T apply(CheckedSupplier<T> action) {
        final policy = retryPolicy()
        return Failsafe.with(policy).get(action)
    }

    static Retryable of(Config config) {
        new Retryable().withConfig(config)
    }
}
