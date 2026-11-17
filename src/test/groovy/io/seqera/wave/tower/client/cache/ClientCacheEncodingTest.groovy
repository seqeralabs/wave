/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.tower.client.cache

import spock.lang.Specification

import java.lang.reflect.Modifier

import io.seqera.cache.tiered.AbstractTieredCache
import io.seqera.serde.Encodable
import io.seqera.wave.tower.client.TowerClient

/**
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class ClientCacheEncodingTest extends Specification {

    /**
     * An unregistered subtype only fails on the L2 write, so it is invisible until Redis is
     * configured. Reflect over the client rather than listing the types by hand.
     */
    def 'should encode every response the tower client caches' () {
        given:
        def encoder = ClientCache.encoder()
        def types = TowerClient.declaredMethods
                .findAll { Modifier.isPublic(it.modifiers) && Encodable.isAssignableFrom(it.returnType) }
                .collect { it.returnType }
                .unique()

        expect: 'the reflection found the exchange classes, not an empty set'
        types.size() >= 6

        when:
        def failed = types.findAll { type ->
            try {
                encoder.encode(new AbstractTieredCache.Entry(type.getDeclaredConstructor().newInstance() as Encodable, 0L))
                return false
            }
            catch (IllegalArgumentException e) {
                return true
            }
        }

        then:
        failed == []
    }

}
