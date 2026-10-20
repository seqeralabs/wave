/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
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

package io.seqera.wave.service.data.workqueue

import groovy.transform.CompileStatic
import io.micrometer.core.instrument.MeterRegistry
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.seqera.data.workqueue.metrics.MicrometerQueueMetrics
import io.seqera.data.workqueue.metrics.QueueMetrics
import jakarta.inject.Singleton

/**
 * Provide the {@link QueueMetrics} injected into the Redis work queue, which records the
 * message lease metrics (leased entries, lease renewal, lost and leaked leases). Without
 * this bean the Redis work queue records no lease metrics
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Factory
@CompileStatic
class QueueMetricsFactory {

    @Singleton
    @Requires(beans = MeterRegistry)
    QueueMetrics queueMetrics(MeterRegistry registry) {
        return new MicrometerQueueMetrics(registry, 'redis-workqueue')
    }
}
