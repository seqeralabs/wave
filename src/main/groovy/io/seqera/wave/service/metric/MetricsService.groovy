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
 * Defines the interface to retrieve wave metrics from redis
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface MetricsService {
    /**
     * get the Wave builds metrics
     *
     * @param date, date of the required metrics
     * @param org, org of the required metrics
     * @return Long, builds counts ]
     */
    Long getBuildsMetrics(String date, String org)

    /**
     * get the Wave pulls metrics
     *
     * @param date, date of the required metrics
     * @param org, org of the required metrics
     * @return Long, pulls counts ]
     */
    Long getPullsMetrics(String date, String org)

    /**
     * get the Wave fusion pulls metrics
     *
     * @param date, date of the required metrics
     * @param org, org of the required metrics
     * @return Long, pulls counts ]
     */
    Long getFusionPullsMetrics(String date, String org)

    void incrementFusionPullsCounter(String token)

    void incrementBuildsCounter(String token)

    void incrementPullsCounter(String token)
}
