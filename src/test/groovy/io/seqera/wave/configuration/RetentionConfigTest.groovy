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

package io.seqera.wave.configuration

import spock.lang.Specification

import java.time.Duration

import io.micronaut.context.ApplicationContext
/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RetentionConfigTest extends Specification {

    def 'should be disabled with the documented defaults'() {
        given:
        def ctx = ApplicationContext.run()

        when:
        def config = ctx.getBean(RetentionConfig)
        then: 'retention is off, so Wave behaves as it does today'
        !config.enabled
        and: 'the periods still carry their defaults, used once it is switched on'
        config.build == Duration.ofDays(90)
        config.request == Duration.ofDays(90)
        config.mirror == Duration.ofDays(90)
        config.scan == Duration.ofDays(5)

        cleanup:
        ctx.close()
    }

    def 'should read the configured periods'() {
        given:
        def ctx = ApplicationContext.run([
                'wave.retention.enabled': true,
                'wave.retention.build'  : '30d',
                'wave.retention.request': '10d',
                'wave.retention.mirror' : '2h',
                'wave.retention.scan'   : '1d' ])

        when:
        def config = ctx.getBean(RetentionConfig)
        then:
        config.enabled
        config.build == Duration.ofDays(30)
        config.request == Duration.ofDays(10)
        config.mirror == Duration.ofHours(2)
        config.scan == Duration.ofDays(1)

        cleanup:
        ctx.close()
    }

    def 'should fail to start naming the offending setting'() {
        when:
        def ctx = ApplicationContext.run([(SETTING): VALUE])
        ctx.getBean(RetentionConfig)
        then:
        def e = thrown(Exception)
        rootCause(e).message.contains(SETTING)

        cleanup:
        ctx?.close()

        where:
        SETTING                   | VALUE
        'wave.retention.build'    | '-1d'
        'wave.retention.request'  | '0s'
        'wave.retention.mirror'   | '-5h'
        'wave.retention.scan'     | '0d'
        'wave.retention.interval' | '-1m'
    }

    def 'should reject a non positive batch size'() {
        when:
        def ctx = ApplicationContext.run(['wave.retention.batch-size': 0])
        ctx.getBean(RetentionConfig)
        then:
        def e = thrown(Exception)
        rootCause(e).message.contains('wave.retention.batch-size')

        cleanup:
        ctx?.close()
    }

    private static Throwable rootCause(Throwable e) {
        def result = e
        while( result.cause && result.cause != result )
            result = result.cause
        return result
    }

}
