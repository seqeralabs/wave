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
    private Map<String,Object> registries

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
