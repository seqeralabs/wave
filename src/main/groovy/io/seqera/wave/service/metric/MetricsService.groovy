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

import io.seqera.wave.service.metric.model.GetOrgCountResponse
import io.seqera.wave.tower.PlatformId
/**
 * Defines the interface to store and retrieve wave metrics
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface MetricsService {
    /**
     * increment wave fusion pulls count
     *
     * @param seqera platform id
     */
    void incrementFusionPullsCounter(PlatformId platformId)

    /**
     * increment wave builds count
     *
     * @param seqera platform id
     */
    void incrementBuildsCounter(PlatformId platformId)

    /**
     * increment wave pulls count
     *
     * @param seqera platform id
     */
    void incrementPullsCounter(PlatformId platformId)

    /**
     * Get counts of all organisations
     *
     * @param metric
     * @return GetOrgCountResponse
     */
    GetOrgCountResponse getAllOrgCount(String metric)

    /**
     * Get counts per organisations or per date or per both
     *
     * @param metric
     * @return GetOrgCountResponse
     */
    GetOrgCountResponse getOrgCount(String metric, String date, String org)
}
