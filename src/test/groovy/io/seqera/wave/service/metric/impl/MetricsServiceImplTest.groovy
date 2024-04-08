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

import spock.lang.Specification
import spock.lang.Unroll

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.seqera.wave.service.counter.impl.LocalCounterProvider
import io.seqera.wave.service.metric.MetricConstants
import io.seqera.wave.service.metric.MetricsCounterStore
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class MetricsServiceImplTest extends Specification {

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    def 'should increment build count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementBuildsCounter(platformId1)
        metricsService.incrementBuildsCounter(platformId2)
        metricsService.incrementBuildsCounter(null)

        then:
        metricsService.getBuildsMetrics(date, null) == 3
        metricsService.getBuildsMetrics(null, 'org1.com') == 1
        metricsService.getBuildsMetrics(date, 'org2.com') == 1
    }

    def 'should increment pull count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementPullsCounter(platformId1)
        metricsService.incrementPullsCounter(platformId2)
        metricsService.incrementPullsCounter(null)

        then:
        metricsService.getPullsMetrics(null, 'org1.com') == 1
        metricsService.getPullsMetrics(date, 'org2.com') == 1
        metricsService.getPullsMetrics(date, null) == 3
    }

    def 'should increment fusion pull count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def metricsService = new MetricsServiceImpl(metricsCounterStore: metricsCounterStore)
        def user1 = new User(id: 1, userName: 'foo', email: 'user1@org1.com')
        def user2 = new User(id: 2, userName: 'bar', email: 'user2@org2.com')
        def platformId1 = new PlatformId(user1, 101)
        def platformId2 = new PlatformId(user2, 102)

        when:
        metricsService.incrementFusionPullsCounter(platformId1)
        metricsService.incrementFusionPullsCounter(platformId2)
        metricsService.incrementFusionPullsCounter(null)

        then:
        metricsService.getFusionPullsMetrics(null, 'org1.com') == 1
        metricsService.getFusionPullsMetrics(date, 'org2.com') == 1
        metricsService.getFusionPullsMetrics(date, null) == 3
    }

    @Unroll
    def'should get correct metrics key'() {
        expect:
        MetricsServiceImpl.getKey(PREFIX, DAY, ORG) == KEY

        where:
        PREFIX                          | DAY           | ORG       | KEY
        MetricConstants.PREFIX_BUILDS   | null          | null      | null
        MetricConstants.PREFIX_BUILDS   | null          | 'wave'    | 'builds/o/wave'
        MetricConstants.PREFIX_BUILDS   | '2024-03-25'  | 'wave'    | 'builds/o/wave/d/2024-03-25'
        MetricConstants.PREFIX_BUILDS   | '2024-03-25'  | null      | 'builds/d/2024-03-25'
        MetricConstants.PREFIX_PULLS    | null          | null      | null
        MetricConstants.PREFIX_PULLS    | null          | 'wave'    | 'pulls/o/wave'
        MetricConstants.PREFIX_PULLS    | '2024-03-25'  | 'wave'    | 'pulls/o/wave/d/2024-03-25'
        MetricConstants.PREFIX_PULLS    | '2024-03-25'  | null      | 'pulls/d/2024-03-25'
        MetricConstants.PREFIX_FUSION   | null          | null      | null
        MetricConstants.PREFIX_FUSION   | null          | 'wave'    | 'fusion/o/wave'
        MetricConstants.PREFIX_FUSION   | '2024-03-25'  | 'wave'    | 'fusion/o/wave/d/2024-03-25'
        MetricConstants.PREFIX_FUSION   | '2024-03-25'  | null      | 'fusion/d/2024-03-25'
    }

    @Unroll
    def'should get correct org name'(){
        expect:
        MetricsServiceImpl.getOrg(EMAIL) == ORG

        where:
        EMAIL               | ORG
        'user@example.com'  | 'example.com'
        'john.doe@test.org' | 'test.org'
        'foo@bar.co.uk'     | 'bar.co.uk'
        'invalid_email'     | null
        null                | null
    }
}
