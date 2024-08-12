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

package io.seqera.wave.service.blob.transfer

import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class TransferTest extends Specification {

    @Unroll
    def 'should validate completed status' () {
        expect:
        new Transfer(STATUS).completed() == EXPECTED
        
        where:
        STATUS                              | EXPECTED
        Transfer.Status.PENDING     | false
        Transfer.Status.RUNNING     | false
        Transfer.Status.UNKNOWN     | false
        and:
        Transfer.Status.SUCCEEDED   | true
        Transfer.Status.FAILED      | true
    }

    @Unroll
    def 'should validate succeeded status' () {
        expect:
        new Transfer(STATUS, EXIT).succeeded() == EXPECTED

        where:
        STATUS                      | EXIT  | EXPECTED
        Transfer.Status.PENDING     | null  | false
        Transfer.Status.RUNNING     | null  | false
        Transfer.Status.UNKNOWN     | null  | false
        Transfer.Status.FAILED      | null  | false
        Transfer.Status.SUCCEEDED   | 1     | false
        Transfer.Status.SUCCEEDED   | 0     | true

    }

}
