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

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.data.stream.impl.LocalMessageStream
import io.seqera.wave.util.LongRndKey
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = ['test'])
class LocalMessageStreamTest extends Specification {

    def 'should offer and consume a value' () {
        given:
        def id1 = "stream-${LongRndKey.rndHex()}"
        def id2 = "stream-${LongRndKey.rndHex()}"
        and:
        def stream = new LocalMessageStream()
        when:
        stream.offer(id1, 'one')
        and:
        stream.offer(id2, 'alpha')
        stream.offer(id2, 'delta')
        stream.offer(id2, 'gamma')

        then:
        stream.consume(id1, { it-> it=='one'})
        and:
        stream.consume(id2, { it-> it=='alpha'})
        stream.consume(id2, { it-> it=='delta'})
        stream.consume(id2, { it-> it=='gamma'})
        and:
        !stream.consume(id2, { it-> assert false /* <-- this should not be invoked */ })
    }

    def 'should offer and consume a value with a failure' () {
        given:
        def id1 = "stream-${LongRndKey.rndHex()}"
        def stream = new LocalMessageStream()
        when:
        stream.offer(id1, 'alpha')
        stream.offer(id1, 'delta')
        stream.offer(id1, 'gamma')

        then:
        stream.consume(id1, { it-> it=='alpha'})
        and:
        !stream.consume(id1, { it-> throw new RuntimeException("Oops")})
        and:
        // next message is 'gamma' as expected
        stream.consume(id1, { it-> it=='gamma'})
        and:
        // now the errored message is available again
        stream.consume(id1, { it-> it=='delta'})
        and:
        !stream.consume(id1, { it-> assert false /* <-- this should not be invoked */ })

        when:
        stream.offer(id1, 'something')
        then:
        stream.consume(id1, { it-> it=='something'})
    }

}
