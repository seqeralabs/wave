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
package io.seqera.wave.service.metric
/**
 * Metrics Service
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface MetricService {
    /**
     * get the build count per metric
     *
     * @param metric, which metric to count
     * @param MetricFilters, to filter container requests for processing
     * @return The {@link Map} of [{@link Metric}, total build count per metric ]
     */
    Map getBuildsMetric(Metric metric, MetricFilter filter)

    /**
     * get the pull count per metric
     *
     * @param metric, which metric to count
     * @param MetricFilters, to filter container requests for processing
     * @return The {@link Map} of [{@link Metric}, total pull count per metric ]
     */
    Map getPullsMetric(Metric metric, MetricFilter filter)

    /**
     * get the total pull count
     *
     * @param MetricFilters, to filter container requests for processing
     * @return The {@link Long} of total pull count
     */
    Long getPullsCount(MetricFilter filter)

    /**
     * get the total build count
     *
     * @param MetricFilters, to filter container requests for processing
     * @return The {@link Long} of total build count
     */
    Long getBuildsCount(MetricFilter filter)

    /**
     * get the total count of distinct metrics
     *
     * @param metric, which metric to count
     * @param MetricFilters, to filter container requests for processing
     * @return The {@link Long} of count of distinct metrics
     */
    Long getDistinctMetrics(Metric metric, MetricFilter filter)

}
