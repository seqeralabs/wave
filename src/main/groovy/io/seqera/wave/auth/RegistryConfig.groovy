/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context

/**
 * Holds container registry static keys as define in the Wave config file
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('wave')
@Context
@CompileStatic
class RegistryConfig {

    /*
     * a Map of map of registries, i.e.
     * [
     *  docker: [
     *      io: [
     *          username: theuser, password: thepwd
     *      ]
     *  ],
     *  quay: [
     *      io: [ ... ]
     *  ]
     */
    Map<String,Object> registries

    RegistryKeys getRegistryKeys(String registryName) {
        final String defaultRegistry = registries.get('default')?.toString() ?: 'docker.io'
        Map map = registryName ? findMap(registryName) : findMap(defaultRegistry)
        if( !map )
            return null
        RegistryKeys ret = new RegistryKeys()
        ret.username = map['username']?.toString() ?: ''
        ret.password = map['password']?.toString() ?: ''
        return ret
    }

    private Map findMap(String registryName){
        def parts = registryName.split('\\.') as List<String>
        def map = registries
        while( parts ){
            if( !map.containsKey(parts.first())){
                map = null
                break
            }
            map = map[parts.first()] as Map<String,Object>
            parts = parts.drop(1)
        }
        return map
    }

    /**
     * Registry static key pair as defined in the Wave config file
     */
    static class RegistryKeys {
        String username
        String password
    }
}
