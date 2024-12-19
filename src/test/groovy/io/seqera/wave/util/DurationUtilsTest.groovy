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

package io.seqera.wave.util

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration

class DurationUtilsTest extends Specification {

    @Unroll
    def "randomDuration returns a duration between #min and #max"() {
        given:
        def minDuration = Duration.ofSeconds(min)
        def maxDuration = Duration.ofSeconds(max)

        when:
        def result = DurationUtils.randomDuration(minDuration, maxDuration)

        then:
        result >= minDuration
        result <= maxDuration

        where:
        min | max
        1   | 10
        0   | 100
        60  | 3600
        3600| 7200
    }

    def "randomDuration returns min or max when they are equal"() {
        given:
        def duration = Duration.ofSeconds(10)

        when:
        def result = DurationUtils.randomDuration(duration, duration)

        then:
        result == duration
    }


    def "randomDuration generates different values over multiple calls"() {
        given:
        def minDuration = Duration.ofSeconds(1)
        def maxDuration = Duration.ofSeconds(1000)
        def iterations = 100
        def results = []

        when:
        iterations.times {
            results << DurationUtils.randomDuration(minDuration, maxDuration)
        }

        then:
        results.unique().size() > 1
    }

    @Unroll
    def "randomDuration returns a duration within #intervalPercentage of #reference"() {
        given:
        def referenceDuration = Duration.ofSeconds(reference)

        when:
        def result = DurationUtils.randomDuration(referenceDuration, intervalPercentage)

        then:
        def minAllowed = referenceDuration.multipliedBy((long)((1 - intervalPercentage) * 100)).dividedBy(100)
        def maxAllowed = referenceDuration.multipliedBy((long)((1 + intervalPercentage) * 100)).dividedBy(100)
        result >= minAllowed
        result <= maxAllowed

        where:
        reference | intervalPercentage
        100       | 0.2f
        60        | 0.5f
        3600      | 0.1f
        10        | 0.0f
    }

    def "randomDuration throws IllegalArgumentException for invalid interval percentage"() {
        given:
        def referenceDuration = Duration.ofSeconds(100)

        when:
        DurationUtils.randomDuration(referenceDuration, invalidPercentage)

        then:
        thrown(IllegalArgumentException)

        where:
        invalidPercentage << [-0.1f, 1.1f]
    }

    def "randomDuration handles zero reference duration"() {
        given:
        def referenceDuration = Duration.ZERO

        when:
        def result = DurationUtils.randomDuration(referenceDuration, 0.2f)

        then:
        result == Duration.ZERO
    }

    def "randomDuration generates different values over multiple calls"() {
        given:
        def referenceDuration = Duration.ofSeconds(100)
        def intervalPercentage = 0.2f
        def iterations = 100
        def results = []

        when:
        iterations.times {
            results << DurationUtils.randomDuration(referenceDuration, intervalPercentage)
        }

        then:
        results.unique().size() > 1
    }
}
