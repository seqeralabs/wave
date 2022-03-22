package io.seqera.config

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import jakarta.inject.Singleton

import javax.annotation.PostConstruct
import javax.validation.constraints.Min
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Context
@Singleton
@ConfigurationProperties("towerreg")
class DefaultConfiguration implements TowerConfiguration{

    @NotNull
    String arch

    @NotNull
    @Size(min=1)
    List<Registry> registries


    Registry getDefaultRegistry(){
        registries.first()
    }

    Registry findRegistry(String name){
        registries.find{ it.name == name} ?: defaultRegistry
    }

}
