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

import spock.lang.Specification

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@MicronautTest
class MetricsCounterStoreLocalTest extends Specification {

    @Inject
    MetricsCounterStore metricsCounterStore

    DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    def 'should get correct count value' () {
        when:
        metricsCounterStore.inc('foo')
        metricsCounterStore.inc('foo')
        metricsCounterStore.inc('bar')

        then:
        metricsCounterStore.get('foo') == 2
        metricsCounterStore.get('bar') == 1
    }

    def 'should get correct org count value' () {
        when:
        metricsCounterStore.inc('builds/o/foo.com')
        metricsCounterStore.inc('builds/o/bar.org')
        metricsCounterStore.inc('pulls/o/bar.in')
        metricsCounterStore.inc('pulls/o/foo.com/d/2024-05-29')
        metricsCounterStore.inc('builds/o/bar.org/d/2024-05-30')
        metricsCounterStore.inc('fusion/o/bar.in/d/2024-05-30')
        metricsCounterStore.inc('pulls/o/bar.in/d/2024-05-31')

        then:
        metricsCounterStore.getAllMatchingEntries('builds/o/*') == ['builds/o/foo.com':1, 'builds/o/bar.org':1, 'builds/o/bar.org/d/2024-05-30':1]
        metricsCounterStore.getAllMatchingEntries('pulls/o/*') == ['pulls/o/bar.in':1, 'pulls/o/foo.com/d/2024-05-29':1, 'pulls/o/bar.in/d/2024-05-31':1]
        metricsCounterStore.getAllMatchingEntries('fusion/o/*') == ['fusion/o/bar.in/d/2024-05-30':1]
        metricsCounterStore.getAllMatchingEntries('builds/o/*/d/2024-05-30') == ['builds/o/bar.org/d/2024-05-30':1]
        metricsCounterStore.getAllMatchingEntries('pulls/o/bar.in/d/2024-05-31') == ['pulls/o/bar.in/d/2024-05-31':1]
    }

    def 'should get correct org count value for mirror and scan' () {
        when:
        metricsCounterStore.inc('mirrors/o/foo.com')
        metricsCounterStore.inc('mirrors/o/bar.org')
        metricsCounterStore.inc('scans/o/bar.in')
        metricsCounterStore.inc('scans/o/foo.com/d/2024-05-29')
        metricsCounterStore.inc('mirrors/o/bar.org/d/2024-05-30')
        metricsCounterStore.inc('scans/o/bar.in/d/2024-05-31')

        then:
        metricsCounterStore.getAllMatchingEntries('mirrors/o/*') == ['mirrors/o/foo.com':1, 'mirrors/o/bar.org':1, 'mirrors/o/bar.org/d/2024-05-30':1]
        metricsCounterStore.getAllMatchingEntries('scans/o/*') == ['scans/o/bar.in':1, 'scans/o/foo.com/d/2024-05-29':1, 'scans/o/bar.in/d/2024-05-31':1]
        metricsCounterStore.getAllMatchingEntries('mirrors/o/*/d/2024-05-30') == ['mirrors/o/bar.org/d/2024-05-30':1]
        metricsCounterStore.getAllMatchingEntries('scans/o/bar.in/d/2024-05-31') == ['scans/o/bar.in/d/2024-05-31':1]
    }
}
