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

package io.seqera.wave.service.view.auth

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.serde.moshi.MoshiSerializable
import io.seqera.service.pairing.PairingEndpoint

/**
 * A viewer's signed-in state, one token per Platform endpoint.
 *
 * The browser only ever holds the opaque session id; the tokens stay server side.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@EqualsAndHashCode
@ToString(includePackage = false, includeNames = true, excludes = 'tokens')
@CompileStatic
class ViewSession implements MoshiSerializable {

    /**
     * Keyed by the canonical endpoint, not the spelling a record carried: the owner keeps the
     * spelling the request used, so two records of one Platform can name it differently and must
     * still share the token this session holds.
     */
    Map<String,String> tokens = new HashMap<>()

    String tokenFor(String towerEndpoint) {
        final key = PairingEndpoint.normalise(towerEndpoint)
        return key ? tokens?.get(key) : null
    }

    ViewSession without(String towerEndpoint) {
        final copy = new HashMap<String,String>(tokens ?: [:])
        copy.remove(PairingEndpoint.normalise(towerEndpoint))
        return new ViewSession(tokens: copy)
    }

    ViewSession withToken(String towerEndpoint, String token) {
        final copy = new HashMap<String,String>(tokens ?: [:])
        copy.put(PairingEndpoint.normalise(towerEndpoint), token)
        return new ViewSession(tokens: copy)
    }

}
