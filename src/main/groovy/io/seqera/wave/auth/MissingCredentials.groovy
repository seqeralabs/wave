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

package io.seqera.wave.auth


import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import io.seqera.wave.util.RegHelper

/**
 * A class that model the absence of registry credentials
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode
@CompileStatic
class MissingCredentials implements RegistryCredentials {

    final String id

    MissingCredentials(String id) {
        this.id = id
    }

    String getUsername() { null }

    String getPassword() { null }

    /**
     * The id is a Platform access token: render a hash of it so that the token is never
     * written to the logs, while the string stays unique per token. This string is part
     * of the registry auth token cache key, see {@code RegistryAuthServiceImpl.CacheKey}
     */
    @Override
    String toString() {
        return "MissingCredentials[${id ? 'id-' + RegHelper.sipHash(id) : id}]"
    }

    /**
     * @return {@code false} by definition
     */
    boolean asBoolean() {
        return false
    }
}
