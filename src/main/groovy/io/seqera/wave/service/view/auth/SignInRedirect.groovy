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

import groovy.transform.Canonical
import groovy.transform.CompileStatic

/**
 * Where to send a browser to sign in, and the value that ties the reply back to it.
 *
 * The caller sets {@code stateHash} as a cookie: the callback only accepts a {@code state}
 * that hashes to it, so a sign-in started elsewhere cannot be planted on this browser.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Canonical
@CompileStatic
class SignInRedirect {
    String url
    String stateHash
    /** Names the cookie that holds {@code stateHash}, so concurrent sign-ins keep one each */
    String state
}
