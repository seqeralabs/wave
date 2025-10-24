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

package io.seqera.wave.tower.client.connector

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.tower.auth.JwtAuth

/**
 * Model cacheable params for JWT refresh request
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class JwtRefreshParams {
    String endpoint
    JwtAuth auth

    boolean equals(object) {
        if (this.is(object)) return true
        if (object == null || getClass() != object.class) return false

        JwtRefreshParams that = (JwtRefreshParams) object

        if (endpoint != that.endpoint) return false
        if (auth.endpoint != that.auth.endpoint) return false
        if (auth.bearer != that.auth.bearer) return false
        if (auth.refresh != that.auth.refresh) return false

        return true
    }

    int hashCode() {
        int result
        result = (endpoint != null ? endpoint.hashCode() : 0)
        result = 31 * result + (auth.endpoint != null ? auth.endpoint.hashCode() : 0)
        result = 31 * result + (auth.bearer != null ? auth.bearer.hashCode() : 0)
        result = 31 * result + (auth.refresh != null ? auth.refresh.hashCode() : 0)
        return result
    }
}
