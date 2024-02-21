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

import spock.lang.Specification

import java.time.Instant
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class MetricFilterTest extends Specification {

    def "should return filter with everything"() {
        given:
        def startDate = Instant.now()
        def endDate = Instant.now()
        def filter = new MetricFilter.Builder()
                .dates(startDate, endDate)
                .limit(10)
                .success(true)
                .fusion(false)
                .build()
        expect:
        filter.startDate == startDate
        filter.endDate == endDate
        filter.limit == 10
        filter.success == true
        filter.fusion == false
    }

    def "should return filter with dates"() {
        given:
        def startDate = Instant.now()
        def endDate = Instant.now()
        def filter = new MetricFilter.Builder().dates(startDate, endDate).build()

        expect:
        filter.startDate == startDate
        filter.endDate == endDate
        filter.limit == 100
        filter.success == null
        filter.fusion == null
    }

    def "should generate endDate when startDate is set"() {
        given:
        def startDate = Instant.now()
        def endDate = null
        def filter = new MetricFilter.Builder().dates(startDate, endDate).build()

        expect:
        filter.startDate == startDate
        filter.endDate != null
        filter.limit == 100
        filter.success == null
        filter.fusion == null
    }

    def "should return filter with limit"() {
        given:
        def filter = new MetricFilter.Builder().limit(1).build()

        expect:
        filter.startDate == null
        filter.endDate == null
        filter.limit == 1
        filter.success == null
        filter.fusion == null
    }

    def "should return filter with success"() {
        given:
        def filter = new MetricFilter.Builder().success(true).build()

        expect:
        filter.startDate == null
        filter.endDate == null
        filter.limit == 100
        filter.success == true
        filter.fusion == null
    }

    def "should return filter with fusion"() {
        given:
        def filter = new MetricFilter.Builder().fusion(false).build()

        expect:
        filter.startDate == null
        filter.endDate == null
        filter.limit == 100
        filter.success == null
        filter.fusion == false
    }

    def "should return filter with limit 1000 if limit is more than 1000"() {
        given:
        def filter = new MetricFilter.Builder().limit(1001).build()

        expect:
        filter.startDate == null
        filter.endDate == null
        filter.limit == 1000
        filter.success == null
        filter.fusion == null
    }
}
