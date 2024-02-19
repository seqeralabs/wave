/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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
package io.seqera.wave.service.metric.impl


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.metric.Metric
import io.seqera.wave.service.metric.MetricFilter
import io.seqera.wave.service.metric.MetricService
import io.seqera.wave.service.persistence.PersistenceService
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements MetricsService
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class MetricServiceImpl implements MetricService {
    @Inject
    private PersistenceService persistenceService

    @Override
    Map<String, Long> getBuildMetrics(Metric metric, MetricFilter filter) {
        persistenceService.getBuildCountByMetrics(metric, filter)
    }

    @Override
    Map<String, Long> getPullMetrics(Metric metric, MetricFilter filter) {
        persistenceService.getPullCountByMetrics(metric, filter)
    }

    @Override
    Long getPullCount(MetricFilter filter) {
        persistenceService.getPullCount(filter)
    }

    @Override
    Long getBuildCount(MetricFilter filter) {
        persistenceService.getBuildCount(filter)
    }

    @Override
    Long getDistinctMetrics(Metric metric, MetricFilter filter) {
        persistenceService.getDistinctMetrics(metric, filter)
    }
}
