package io.seqera.wave.auth

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Value
import io.micronaut.core.convert.format.MapFormat
import io.micronaut.core.naming.conventions.StringConvention
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton


/**
 * A factory of configuration repositories
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('wave')
@Context
@CompileStatic
class RegistryConfigurationFactory {

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

    RegistryConfiguration findConfiguration(String repository) {
        final String defaultRegistry = registries.get('default')?.toString() ?: 'docker.io'
        Map map = repository ? findMap(repository) : findMap(defaultRegistry)
        if( !map )
            return null
        RegistryConfiguration ret = new RegistryConfiguration()
        ret.name = repository
        ret.username = map['username']?.toString() ?: ''
        ret.password = map['password']?.toString() ?: ''
        return ret
    }

    private Map findMap(String repository){
        def parts = repository.split('\\.') as List<String>
        def map = registries
        while( parts ){
            if( !map.containsKey(parts.first())){
                map = null
                break
            }
            map = map[parts.first()] as Map<String,Object>
            parts = parts.drop(1)
        }
        map
    }

}
