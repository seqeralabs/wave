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

import java.security.Key
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.seqera.wave.service.counter.impl.LocalCounterProvider
import io.seqera.wave.service.license.CheckTokenResponse
import io.seqera.wave.service.license.LicenseManClient
import io.seqera.wave.service.metric.MetricsCounterStore
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class MetricsServiceImplTest extends Specification {

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    def 'should increment a builds count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def licMan = Mock(LicenseManClient)
        def metricsService = new MetricsServiceImpl(licenseManager: licMan, metricsCounterStore: metricsCounterStore)

        when:
        metricsService.incrementBuildsCounter('token1')
        then:
        1 * licMan.checkToken('token1', _) >> new CheckTokenResponse(organization: 'org1')

        when:
        metricsService.incrementBuildsCounter('token2')
        then:
        1 * licMan.checkToken('token2', _) >> new CheckTokenResponse(organization: 'org2')

        when:
        metricsService.incrementBuildsCounter(null)
        then:
        0 * licMan.checkToken(_, _)

        and:
        metricsService.getBuildsMetrics(date, null) == 3
        metricsService.getBuildsMetrics(null, 'org1') == 1
        metricsService.getBuildsMetrics(date, 'org2') == 1

    }

    def 'should increment a pulls count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def licMan = Mock(LicenseManClient)
        def metricsService = new MetricsServiceImpl(licenseManager: licMan, metricsCounterStore: metricsCounterStore)

        when:
        metricsService.incrementPullsCounter('token1')
        then:
        1 * licMan.checkToken('token1', _) >> new CheckTokenResponse(organization: 'org1')

        when:
        metricsService.incrementPullsCounter('token2')
        then:
        1 * licMan.checkToken('token2', _) >> new CheckTokenResponse(organization: 'org2')

        when:
        metricsService.incrementPullsCounter(null)
        then:
        0 * licMan.checkToken(_, _)

        and:
        metricsService.getPullsMetrics(null, 'org1') == 1
        metricsService.getPullsMetrics(date, 'org2') == 1
        metricsService.getPullsMetrics(date, null) == 3

    }

    def 'should increment a fusion pulls count' () {
        given:
        def date = LocalDate.now().format(dateFormatter)
        def localCounterProvider = new LocalCounterProvider()
        def metricsCounterStore = new MetricsCounterStore(localCounterProvider)
        def licMan = Mock(LicenseManClient)
        def metricsService = new MetricsServiceImpl(licenseManager: licMan, metricsCounterStore: metricsCounterStore)

        when:
        metricsService.incrementFusionPullsCounter('token1')
        then:
        1 * licMan.checkToken('token1', _) >> new CheckTokenResponse(organization: 'org1')

        when:
        metricsService.incrementFusionPullsCounter('token2')
        then:
        1 * licMan.checkToken('token2', _) >> new CheckTokenResponse(organization: 'org2')

        when:
        metricsService.incrementFusionPullsCounter(null)
        then:
        0 * licMan.checkToken(_, _)

        and:
        metricsService.getFusionPullsMetrics(null, 'org1') == 1
        metricsService.getFusionPullsMetrics(date, 'org2') == 1
        metricsService.getFusionPullsMetrics(date, null) == 3

    }

    @Unroll
    def'should get correct builds key'() {
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
    def'should get correct pulls key'() {
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
    def'should get correct build key'() {
        expect:
        MetricsServiceImpl.getFusionPullsKey(DAY, ORG) == KEY

        where:
        DAY             | ORG       | KEY
        null            | null      | null
        null            | 'wave'    | 'fusion/o/wave'
        '2024-03-25'    | 'wave'    | 'fusion/o/wave/d/2024-03-25'
        '2024-03-25'    | null      | 'fusion/d/2024-03-25'
    }
}
