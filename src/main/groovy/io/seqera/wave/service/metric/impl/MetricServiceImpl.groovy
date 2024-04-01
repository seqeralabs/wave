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
 * Implements service to retrieve wave metrics
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
    Map<String, Long> getBuildsMetric(Metric metric, MetricFilter filter) {
        persistenceService.getBuildsCountByMetric(metric, filter)
    }

    @Override
    Map<String, Long> getPullsMetric(Metric metric, MetricFilter filter) {
        persistenceService.getPullsCountByMetric(metric, filter)
    }

    @Override
    Long getPullsCount(MetricFilter filter) {
        persistenceService.getPullsCount(filter)
    }

    @Override
    Long getBuildsCount(MetricFilter filter) {
        persistenceService.getBuildsCount(filter)
    }

}
