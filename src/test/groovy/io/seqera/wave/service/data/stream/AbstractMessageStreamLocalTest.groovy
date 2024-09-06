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

package io.seqera.wave.service.data.stream

import spock.lang.Specification

import java.util.concurrent.ArrayBlockingQueue

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.data.stream.impl.LocalMessageStream
import io.seqera.wave.util.LongRndKey
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ['test'])
class AbstractMessageStreamLocalTest extends Specification {

    @Inject
    LocalMessageStream target

    def 'should offer and consume some messages' () {
        given:
        def id1 = "stream-${LongRndKey.rndHex()}"
        and:
        def stream = new TestStream(target)
        def queue = new ArrayBlockingQueue(10)
        and:
        stream.addConsumer(id1, { it-> queue.add(it) })

        when:
        stream.offer(id1, new TestMessage('one','two'))
        stream.offer(id1, new TestMessage('alpha','omega'))
        then:
        queue.take()==new TestMessage('one','two')
        queue.take()==new TestMessage('alpha','omega')
        
        cleanup:
        stream.close()
    }

}
