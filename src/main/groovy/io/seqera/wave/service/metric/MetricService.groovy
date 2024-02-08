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

import java.time.Instant

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
     * @param success, filter only successful builds or not
     * @param startDate, the start date to filter build records for counting
     * @param endDate, the end date to filter build records for counting
     * @return The {@link Map} of [{@link Metric}, total build count per metric ]
     */
    Map getBuildMetrics(Metric metrics, Boolean success, Instant startDate, Instant endDate)

    /**
     * get the pull count per metric
     *
     * @param metric, which metric to count
     * @param startDate, the start date to filter pull records for counting
     * @param endDate, the end date to filter pull records for counting
     * @return The {@link Map} of [{@link Metric}, total pull count per metric ]
     */
    Map getPullMetrics(Metric metrics, Instant startDate, Instant endDate)

    /**
     * get the total pull count
     *
     * @param startDate, the start date to filter pull records for counting
     * @param endDate, the end date to filter pull records for counting
     * @return The {@link Long} of total pull count
     */
    Long getPullCount(Instant startDate, Instant endDate)

    /**
     * get the total build count
     *
     * @param success, filter only successful builds or not
     * @param startDate, the start date to filter build records for counting
     * @param endDate, the end date to filter build records for counting
     * @return The {@link Long} of total build count
     */
    Long getBuildCount(Boolean success, Instant startDate, Instant endDate)

    /**
     * get the total count of distinct metrics
     *
     * @param metric, which metric to count
     * @param startDate, the start date to filter container requests for counting
     * @param endDate, the end date to filter container requests for counting
     * @return The {@link Long} of count of distinct metrics
     */
    Long getDistinctMetrics(Metric metrics, Instant startDate, Instant endDate)
}
