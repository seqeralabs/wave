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
import groovy.util.logging.Slf4j
import io.seqera.wave.util.StringUtils

/**
 * Model the container registry keys as stored in Tower
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class ContainerRegistryKeys {
    /**
     * The registry user name
     */
    String userName

    /**
     * The registry secret
     */
    String password

    /**
     * The registry target host - NOTE: this can be null when the keys where obtained by AWS credentials record
     */
    String registry

    static ContainerRegistryKeys fromJson(String json) {
        final root = (Map) new JsonSlurper().parseText(json)
        // parse container registry credentials
        if( root.discriminator == 'container-reg' ) {
            return new ContainerRegistryKeys(userName: root.userName, password: root.password, registry: root.registry)
        }
        // Map AWS keys to registry username and password
        if( root.discriminator == 'aws' ) {
            if( root.assumeRoleArn ) {
                log.debug "Using AWS IAM role authentication - roleArn=${root.assumeRoleArn}; externalId=${root.externalId ? 'present' : 'missing'}"
                return new ContainerRegistryKeys(userName: root.assumeRoleArn, password: root.externalId)
            }
            return new ContainerRegistryKeys(userName: root.accessKey, password: root.secretKey)
        }
        throw new IllegalArgumentException("Unsupported credentials key discriminator type: ${root.discriminator}")
    }

    @Override
    String toString() {
        return "ContainerRegistryKeys[registry=${registry}; userName=${userName}; password=${StringUtils.redact(password)})]"
    }
}
