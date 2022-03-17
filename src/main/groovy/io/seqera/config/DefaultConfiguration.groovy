package io.seqera.config

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.inject.Singleton

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Singleton
@ConfigurationProperties("towerreg")
class DefaultConfiguration implements TowerConfiguration{

    String arch
    List<Registry> registries

    Registry getDefaultRegistry(){
        registries.first()
    }

    Registry findRegistry(String name){
        registries.find{ it.name == name} ?: defaultRegistry
    }

}
