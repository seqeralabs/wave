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

package io.seqera.wave.cron

import spock.lang.Specification

import io.seqera.wave.configuration.RetentionConfig
import io.seqera.wave.service.retention.RetentionLockStore
import io.seqera.wave.service.retention.RetentionService
/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class RetentionCronTest extends Specification {

    private RetentionCron cron(RetentionService svc, RetentionLockStore lock) {
        def result = new RetentionCron()
        result.@config = new RetentionConfig()
        result.@retentionService = svc
        result.@lockStore = lock
        result.@owner = 'host/1'
        return result
    }

    def 'should purge when it wins the lease and release it afterwards'() {
        given:
        def svc = Mock(RetentionService)
        def lock = Mock(RetentionLockStore)

        when:
        cron(svc, lock).purge()

        then:
        1 * lock.acquire('host/1') >> true
        1 * svc.purge() >> 5
        1 * lock.release('host/1')
    }

    def 'should skip the pass when another replica holds the lease'() {
        given:
        def svc = Mock(RetentionService)
        def lock = Mock(RetentionLockStore)

        when:
        cron(svc, lock).purge()

        then:
        1 * lock.acquire('host/1') >> false
        and: 'the pass does not run'
        0 * svc.purge()
        and: 'and it must not release a lease it never took'
        0 * lock.release(_)
    }

    def 'should release the lease and stay alive when a pass fails'() {
        given:
        def svc = Mock(RetentionService)
        def lock = Mock(RetentionLockStore)

        when:
        cron(svc, lock).purge()

        then:
        1 * lock.acquire('host/1') >> true
        1 * svc.purge() >> { throw new RuntimeException('Database is down') }
        and: 'a failed pass does not kill the scheduler'
        noExceptionThrown()
        and: 'and does not wedge the lease until its TTL'
        1 * lock.release('host/1')
    }

}
