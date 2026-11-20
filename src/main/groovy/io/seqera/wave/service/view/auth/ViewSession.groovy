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

    Map<String,String> tokens = new HashMap<>()

    /** When each token was issued, so a refusal moments after sign-in is not met with another one */
    Map<String,Long> issuedAt = new HashMap<>()

    String tokenFor(String towerEndpoint) {
        return towerEndpoint ? tokens?.get(towerEndpoint) : null
    }

    Long issuedAtFor(String towerEndpoint) {
        return towerEndpoint ? issuedAt?.get(towerEndpoint) : null
    }

    ViewSession without(String towerEndpoint) {
        final copy = new HashMap<String,String>(tokens ?: [:])
        final when = new HashMap<String,Long>(issuedAt ?: [:])
        copy.remove(towerEndpoint)
        when.remove(towerEndpoint)
        return new ViewSession(tokens: copy, issuedAt: when)
    }

    ViewSession withToken(String towerEndpoint, String token) {
        final copy = new HashMap<String,String>(tokens ?: [:])
        final when = new HashMap<String,Long>(issuedAt ?: [:])
        copy.put(towerEndpoint, token)
        when.put(towerEndpoint, System.currentTimeMillis())
        return new ViewSession(tokens: copy, issuedAt: when)
    }

}
