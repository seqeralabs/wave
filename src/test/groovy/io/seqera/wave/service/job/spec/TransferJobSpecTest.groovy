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

package io.seqera.wave.service.job.spec

import spock.lang.Specification

import java.time.Duration
import java.time.Instant

import io.seqera.wave.service.job.JobSpec

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TransferJobSpecTest extends Specification {

    def 'should validate constructor' () {

        given:
        def now = Instant.now()
        def job = new TransferJobSpec('12345', now, Duration.ofMinutes(1), 'xyz')
        expect:
        job.id == '12345'
        job.type == JobSpec.Type.Transfer
        job.creationTime == now
        job.maxDuration == Duration.ofMinutes(1)
        job.schedulerId == 'xyz'
    }

}
