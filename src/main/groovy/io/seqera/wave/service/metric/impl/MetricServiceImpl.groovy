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

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.metric.Metric
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
@Requires(property = 'wave.metrics.enabled', value = 'true')
@Singleton
@CompileStatic
class MetricServiceImpl implements MetricService{
    @Inject
    private PersistenceService persistenceService

    @Override
    Map getBuildMetrics(Metric metrics, Boolean success, Instant startDate, Instant endDate) {
        persistenceService.getBuildCountByMetrics(metrics, success, startDate, endDate)
    }

    @Override
    Map getPullMetrics(Metric metrics, Instant startDate, Instant endDate) {
        persistenceService.getPullCountByMetrics(metrics, startDate, endDate)
    }

    @Override
    Long getPullCount(Instant startDate, Instant endDate) {
        persistenceService.getPullCount(startDate, endDate)
    }

    @Override
    Long getBuildCount(Boolean success, Instant startDate, Instant endDate) {
        persistenceService.getBuildCount(success, startDate, endDate)
    }

    @Override
    Long getDistinctMetrics(Metric metrics, Instant startDate, Instant endDate) {
        persistenceService.getDistinctMetrics(metrics, startDate, endDate)
    }
}
