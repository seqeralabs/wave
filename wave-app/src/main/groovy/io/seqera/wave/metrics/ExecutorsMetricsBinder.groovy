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

package io.seqera.wave.metrics

import java.lang.reflect.Field
import java.util.concurrent.ForkJoinPool

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.binder.jvm.ExecutorServiceMetrics
import io.micronaut.context.annotation.Context
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
/**
 * Register Micrometer metrics for ForkJoin commonPool and virtual threads scheduler
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Context
@CompileStatic
class ExecutorsMetricsBinder {

    @Inject
    private MeterRegistry registry

    @PostConstruct
    void register() {
        log.info "+ Registering executor metrics binder"
        registerCommonPoolMetrics(registry)
        registerVirtualThreadPoolMetrics(registry)
    }

    void registerCommonPoolMetrics(MeterRegistry registry) {
        final commonPool = ForkJoinPool.commonPool()
        ExecutorServiceMetrics.monitor(registry, commonPool, "ForkJoin.commonPool")
    }

    void registerVirtualThreadPoolMetrics(MeterRegistry registry) {
        try {
            // Create a virtual thread executor
            Class<?> VirtualThread = Class.forName("java.lang.VirtualThread");

            // Use reflection to get the internal ForkJoinPool
            Field poolField = VirtualThread.getDeclaredField("DEFAULT_SCHEDULER");
            poolField.setAccessible(true);
            ForkJoinPool virtualThreadPool = (ForkJoinPool) poolField.get(null);

            // Register metrics for the virtual thread pool
            ExecutorServiceMetrics.monitor(registry, virtualThreadPool, "ForkJoin.virtualPool")
        }
        catch (Exception e) {
            log.warn "Unable to registry carrier threads pool metrics", e
        }
    }
}
