package io.seqera.config

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

import io.micronaut.context.annotation.ConfigurationInject
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.micronaut.core.bind.annotation.Bindable
import jakarta.inject.Singleton
/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Context
@Singleton
@ConfigurationProperties("towerreg")
class DefaultConfiguration implements TowerConfiguration{

    @Value('${username}')
    String username

    private String arch
    String getArch(){
        this.arch
    }

    private List<Registry> registries
    List<Registry> getRegistries(){
        this.registries
    }

    @ConfigurationInject
    DefaultConfiguration(@Bindable @NotBlank String arch,
                         @Bindable @Size(min = 1) List<Registry> registries){
        this.arch = arch
        this.registries = registries
    }

    Registry getDefaultRegistry(){
        registries.first()
    }

    Registry findRegistry(String name){
        registries.find{ it.name == name} ?: defaultRegistry
    }

    String toString() {
        return "DefaultConfiguration[arch=$arch; registries=${registries.join(',')}]"
    }
}
