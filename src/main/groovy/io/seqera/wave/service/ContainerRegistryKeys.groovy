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

package io.seqera.wave.service

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import io.seqera.wave.util.StringUtils

/**
 * Model the container registry keys as stored in Tower
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ContainerRegistryKeys {
    String userName
    String password
    String registry

    static ContainerRegistryKeys fromJson(String json) {
        final root = (Map) new JsonSlurper().parseText(json)
        return new ContainerRegistryKeys(userName: root.userName, password: root.password, registry: root.registry)
    }

    @Override
    String toString() {
        return "ContainerRegistryKeys[registry=$registry; userName=$userName; password=${StringUtils.redact(password)})]"
    }
}
