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
import io.seqera.wave.service.metric.MetricsCounterStore
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

        when:
        metricsService.incrementBuildsCounter('user1@org1.com')
        metricsService.incrementBuildsCounter('user2@org2.com')
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

        when:
        metricsService.incrementPullsCounter('user1@org1.com')
        metricsService.incrementPullsCounter('user2@org2.com')
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

        when:
        metricsService.incrementFusionPullsCounter('user1@org1.com')
        metricsService.incrementFusionPullsCounter('user2@org2.com')
        metricsService.incrementFusionPullsCounter(null)

        then:
        metricsService.getFusionPullsMetrics(null, 'org1.com') == 1
        metricsService.getFusionPullsMetrics(date, 'org2.com') == 1
        metricsService.getFusionPullsMetrics(date, null) == 3

    }

    @Unroll
    def'should get correct builds metrics key'() {
        expect:
        MetricsServiceImpl.getBuildsKey(DAY, ORG) == KEY

        where:
        DAY             | ORG       | KEY
        null            | null      | null
        null            | 'wave'    | 'builds/o/wave'
        '2024-03-25'    | 'wave'    | 'builds/o/wave/d/2024-03-25'
        '2024-03-25'    | null      | 'builds/d/2024-03-25'
    }

    @Unroll
    def'should get correct pulls metrics key'() {
        expect:
        MetricsServiceImpl.getPullsKey(DAY, ORG) == KEY

        where:
        DAY             | ORG       | KEY
        null            | null      | null
        null            | 'wave'    | 'pulls/o/wave'
        '2024-03-25'    | 'wave'    | 'pulls/o/wave/d/2024-03-25'
        '2024-03-25'    | null      | 'pulls/d/2024-03-25'
    }

    @Unroll
    def'should get correct fusion pulls metrics key'() {
        expect:
        MetricsServiceImpl.getFusionPullsKey(DAY, ORG) == KEY

        where:
        DAY             | ORG       | KEY
        null            | null      | null
        null            | 'wave'    | 'fusion/o/wave'
        '2024-03-25'    | 'wave'    | 'fusion/o/wave/d/2024-03-25'
        '2024-03-25'    | null      | 'fusion/d/2024-03-25'
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
