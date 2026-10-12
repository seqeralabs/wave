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

package io.seqera.wave.service.counter.impl

import spock.lang.Specification

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ['test'])
class LocalCounterProviderTest extends Specification {

    @Inject
    LocalCounterProvider localCounterProvider

    def 'should increment a counter value' () {
        expect:
        localCounterProvider.inc('build-x', 'foo', 1) == 1
        localCounterProvider.inc('build-x', 'foo', 1) == 2
        and:
        localCounterProvider.inc('build-x', 'foo', 10) == 12
        and:
        localCounterProvider.inc('build-x', 'foo', -12) == 0
    }
  
    def 'should get correct count value' () {
        when:
        localCounterProvider.inc('build-x', 'foo', 1)
        localCounterProvider.inc('build-x', 'foo', 1)
        localCounterProvider.inc('metrics-x', 'foo', 1)

        then:
        localCounterProvider.get('build-x', 'foo') == 2
        and:
        localCounterProvider.get('metrics-x', 'foo') == 1
    }

    def 'should get correct org count' () {
        when:
        localCounterProvider.inc('metrics/v1', 'builds/o/foo.com', 1)
        localCounterProvider.inc('metrics/v1', 'builds/o/bar.io', 1)
        localCounterProvider.inc('metrics/v1', 'builds/o/abc.org', 2)
        localCounterProvider.inc('metrics/v1', 'pulls/o/foo.it', 1)
        localCounterProvider.inc('metrics/v1', 'pulls/o/bar.es', 2)
        localCounterProvider.inc('metrics/v1', 'pulls/o/abc.in', 3)
        localCounterProvider.inc('metrics/v1', 'pulls/o/abc.com.au/d/2024-05-30', 1)
        localCounterProvider.inc('metrics/v1', 'pulls/o/abc.com.au/d/2024-05-31', 1)

        then:
        localCounterProvider.getAllMatchingEntries('metrics/v1', 'pulls/o/*') ==
                ['pulls/o/abc.in':3, 'pulls/o/bar.es':2, 'pulls/o/foo.it':1, 'pulls/o/abc.com.au/d/2024-05-30':1, 'pulls/o/abc.com.au/d/2024-05-31':1]
        and:
        localCounterProvider.getAllMatchingEntries('metrics/v1', 'pulls/o/*/d/2024-05-30') ==
                ['pulls/o/abc.com.au/d/2024-05-30':1]
    }

    def 'should get correct org count for mirror and scan' () {
        when:
        localCounterProvider.inc('metrics/v1', 'mirrors/o/foo.com', 1)
        localCounterProvider.inc('metrics/v1', 'mirrors/o/bar.io', 1)
        localCounterProvider.inc('metrics/v1', 'mirrors/o/abc.org', 2)
        localCounterProvider.inc('metrics/v1', 'scans/o/foo.it', 1)
        localCounterProvider.inc('metrics/v1', 'scans/o/bar.es', 2)
        localCounterProvider.inc('metrics/v1', 'scans/o/abc.in', 3)
        localCounterProvider.inc('metrics/v1', 'scans/o/abc.com.au/d/2024-05-30', 1)
        localCounterProvider.inc('metrics/v1', 'scans/o/abc.com.au/d/2024-05-31', 1)

        then:
        localCounterProvider.getAllMatchingEntries('metrics/v1', 'scans/o/*') ==
                ['scans/o/abc.in':3, 'scans/o/bar.es':2, 'scans/o/foo.it':1, 'scans/o/abc.com.au/d/2024-05-30':1, 'scans/o/abc.com.au/d/2024-05-31':1]
        and:
        localCounterProvider.getAllMatchingEntries('metrics/v1', 'scans/o/*/d/2024-05-30') ==
                ['scans/o/abc.com.au/d/2024-05-30':1]
        and:
        localCounterProvider.getAllMatchingEntries('metrics/v1', 'mirrors/o/*') ==
                ['mirrors/o/foo.com':1, 'mirrors/o/bar.io':1, 'mirrors/o/abc.org':2]
    }

}
