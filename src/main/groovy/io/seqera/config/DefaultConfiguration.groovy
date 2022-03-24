package io.seqera.config

import javax.annotation.PostConstruct
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.core.bind.annotation.Bindable

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Context
@ConfigurationProperties("towerreg")
class DefaultConfiguration implements TowerConfiguration{

    @NotBlank
    private String arch

    String getArch(){
        this.arch
    }

    @Size(min = 1)
    private List<RegistryConfiguration> registries = []

    @Size(min = 1)
    private List<AuthConfiguration> auths = []

    List<RegistryConfiguration> getRegistries(){
        this.registries
    }

    List<AuthConfiguration> getAuths(){
        this.auths
    }

    Registry getDefaultRegistry(){
        registries.first()
    }

    Registry findRegistry(String name){
        registries.find{ it.name == name} ?: defaultRegistry
    }

    @PostConstruct
    private void init(){
        registries.each {r ->
            r.auth = auths.find{ it.name == r.authConfig }
        }
    }
}
