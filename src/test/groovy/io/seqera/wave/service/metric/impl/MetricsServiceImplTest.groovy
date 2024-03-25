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

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject

/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest(environments = ['test'])
class MetricsServiceImplTest extends Specification {

    @Inject
    MetricsServiceImpl metricsService

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    def 'should increment a builds count' () {
        given:
        def org = 'wave'
        def date = LocalDate.now().format(dateFormatter)

        when:
        metricsService.incrementBuildsCounter(org)
        then:
        metricsService.getBuildsMetrics(null, org) == 1
        metricsService.getBuildsMetrics(date, org) == 1
        when:
        metricsService.incrementBuildsCounter(null)
        then:
        metricsService.getBuildsMetrics(date, null) == 2

    }

    def 'should increment a pulls count' () {
        given:
        def org = 'wave'
        def date = LocalDate.now().format(dateFormatter)

        when:
        metricsService.incrementPullsCounter(org)
        then:
        metricsService.getPullsMetrics(null, org) == 1
        metricsService.getPullsMetrics(date, org) == 1
        when:
        metricsService.incrementPullsCounter(null)
        then:
        metricsService.getPullsMetrics(date, null) == 2

    }

    def 'should increment a fusion pulls count' () {
        given:
        def org = 'wave'
        def date = LocalDate.now().format(dateFormatter)

        when:
        metricsService.incrementFusionPullsCounter(org)
        then:
        metricsService.getFusionPullsMetrics(null, org) == 1
        metricsService.getFusionPullsMetrics(date, org) == 1
        when:
        metricsService.incrementFusionPullsCounter(null)
        then:
        metricsService.getFusionPullsMetrics(date, null) == 2

    }
}
