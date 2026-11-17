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
 * A sign-in in flight, held against its {@code state} until the callback returns.
 *
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@EqualsAndHashCode
@ToString(includePackage = false, includeNames = true, excludes = 'codeVerifier')
@CompileStatic
class PendingSignIn implements MoshiSerializable {

    String towerEndpoint

    String codeVerifier

    /** Where to send the browser once signed in; only ever a path on this host */
    String returnTo

}
