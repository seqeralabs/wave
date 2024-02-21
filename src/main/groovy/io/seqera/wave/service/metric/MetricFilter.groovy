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
package io.seqera.wave.service.metric


import java.time.Instant

import groovy.transform.CompileStatic
/**
 * This class contains filters to be applied on data to get desired metrics data
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
class MetricFilter {

    /*
     * startDate for the wave records to be considered for metrics calculation
     */
    private Instant startDate

    /*
     * endDate for the wave records to be considered for metrics calculation
     */
    private Instant endDate

    /*
     * Limit the number of entries in metrics api response
     */
    private Integer limit

    /*
    * success filters the wave successful builds
    */
    private Boolean success

    /*
     * fusion filters the wave containers requests with fusion
     */
    private Boolean fusion

    private MetricFilter(Instant startDate, Instant endDate, Integer limit, Boolean success, Boolean fusion) {
        this.startDate = startDate
        this.endDate = endDate
        this.limit = limit
        this.success = success
        this.fusion = fusion
    }

    static class Builder {
        private Instant startDate
        private Instant endDate
        private Integer limit
        private Boolean success
        private Boolean fusion

        Builder dates(Instant startDate, Instant endDate){
            this.startDate = startDate
            this.endDate = endDate?:startDate?Instant.now():null
            return this
        }

        Builder limit(Integer limit) {
            this.limit = limit
            return this
        }

        Builder success(Boolean success){
            this.success = success
            return this
        }

        Builder fusion(Boolean fusion){
            this.fusion = fusion
            return this
        }

        MetricFilter build(){
            //set min, max and default limit
            limit = limit?:100
            limit = limit<1?1:limit
            limit = limit>1000?1000:limit

            return new MetricFilter(startDate, endDate, limit, success, fusion)
        }
    }
    Instant getStartDate() {
        return startDate
    }

    Instant getEndDate() {
        return endDate
    }

    Integer getLimit() {
        return limit
    }

    Boolean getSuccess() {
        return success
    }

    Boolean getFusion() {
        return fusion
    }
}
