package io.seqera.config

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.inject.Singleton

import javax.annotation.PostConstruct

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Singleton
@ConfigurationProperties("towerreg")
class DefaultConfiguration implements TowerConfiguration{

    String arch
    List<Registry> registries

    @PostConstruct
    void init(){
        assert arch, "towerreg.arch configuration is required"
        assert registries , "at least a Registry in towerreg.registries is required"
    }

    Registry getDefaultRegistry(){
        registries.first()
    }

    Registry findRegistry(String name){
        registries.find{ it.name == name} ?: defaultRegistry
    }

}
