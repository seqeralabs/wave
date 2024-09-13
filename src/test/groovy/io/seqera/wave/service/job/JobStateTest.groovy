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

package io.seqera.wave.service.job


import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JobStateTest extends Specification {

    @Unroll
    def 'should validate completed status' () {
        expect:
        new JobState(STATUS).completed() == EXPECTED
        
        where:
        STATUS                              | EXPECTED
        JobState.Status.PENDING   | false
        JobState.Status.RUNNING   | false
        JobState.Status.UNKNOWN   | false
        and:
        JobState.Status.SUCCEEDED | true
        JobState.Status.FAILED    | true
    }

    @Unroll
    def 'should validate succeeded status' () {
        expect:
        new JobState(STATUS, EXIT).succeeded() == EXPECTED

        where:
        STATUS                      | EXIT  | EXPECTED
        JobState.Status.PENDING     | null | false
        JobState.Status.RUNNING     | null | false
        JobState.Status.UNKNOWN     | null | false
        JobState.Status.FAILED      | null | false
        JobState.Status.SUCCEEDED   | 1    | false
        JobState.Status.SUCCEEDED   | 0    | true

    }

}
