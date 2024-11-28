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
import io.micronaut.context.annotation.Context
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
/**
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

    void registerCommonPoolMetrics(MeterRegistry meterRegistry) {
        ForkJoinPool commonPool = ForkJoinPool.commonPool();

        meterRegistry.gauge("common.pool.size", commonPool, ForkJoinPool::getPoolSize);
        meterRegistry.gauge("common.active.thread.count", commonPool, ForkJoinPool::getActiveThreadCount);
        meterRegistry.gauge("common.queued.submissions", commonPool, ForkJoinPool::getQueuedSubmissionCount);
        meterRegistry.gauge("common.queued.tasks", commonPool, ForkJoinPool::getQueuedTaskCount);
        meterRegistry.gauge("common.parallelism", commonPool, ForkJoinPool::getParallelism);
        meterRegistry.gauge("common.steal.count", commonPool, ForkJoinPool::getStealCount);
    }

    void registerVirtualThreadPoolMetrics(MeterRegistry meterRegistry) {
        try {
            // Create a virtual thread executor
            Class<?> VirtualThread = Class.forName("java.lang.VirtualThread");

            // Use reflection to get the internal ForkJoinPool
            Field poolField = VirtualThread.getDeclaredField("DEFAULT_SCHEDULER");
            poolField.setAccessible(true);
            ForkJoinPool virtualThreadPool = (ForkJoinPool) poolField.get(null);

            // Register metrics for the virtual thread pool
            meterRegistry.gauge("virtual.pool.size", virtualThreadPool, ForkJoinPool::getPoolSize);
            meterRegistry.gauge("virtual.active.thread.count", virtualThreadPool, ForkJoinPool::getActiveThreadCount);
            meterRegistry.gauge("virtual.queued.submissions", virtualThreadPool, ForkJoinPool::getQueuedSubmissionCount);
            meterRegistry.gauge("virtual.queued.tasks", virtualThreadPool, ForkJoinPool::getQueuedTaskCount);
            meterRegistry.gauge("virtual.parallelism", virtualThreadPool, ForkJoinPool::getParallelism);
            meterRegistry.gauge("virtual.steal.count", virtualThreadPool, ForkJoinPool::getStealCount);
        }
        catch (Exception e) {
            log.error "Unable to registry carrier threads pool metrics", e
        }
    }
}
