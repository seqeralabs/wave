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
import groovy.transform.ToString
import io.micronaut.context.annotation.EachProperty
import io.seqera.service.pairing.PairingEndpoint
import io.micronaut.context.annotation.Parameter

/**
 * The OIDC client registered for one paired Platform.
 *
 * Each entry is its own named block rather than a packed value under a URL key, so it binds from
 * environment variables and a secret containing '|' survives:
 *
 * <pre>
 * wave.view.auth.clients.cloud.endpoint      = https://api.cloud.seqera.io
 * wave.view.auth.clients.cloud.client-id     = ...
 * wave.view.auth.clients.cloud.client-secret = ...
 * </pre>
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@EachProperty('wave.view.auth.clients')
@ToString(includeNames = true, includePackage = false, excludes = 'clientSecret')
@CompileStatic
class ViewAuthClient {

    final String name

    String endpoint

    String clientId

    String clientSecret

    ViewAuthClient(@Parameter String name) {
        this.name = name
    }

    /** The form the pairing key and the tenant use, so a spelling that pairs can also sign in */
    static String key(String endpoint) {
        return PairingEndpoint.normalise(endpoint)
    }

    boolean matches(String towerEndpoint) {
        return endpoint && key(endpoint) == key(towerEndpoint)
    }

}
