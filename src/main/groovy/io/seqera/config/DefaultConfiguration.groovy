package io.seqera.config

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Context
@ConfigurationProperties("towerreg")
class DefaultConfiguration implements TowerConfiguration {

    @NotBlank
    private String arch

    @Size(min = 1)
    private List<RegistryConfiguration> registries

    Registry getDefaultRegistry() {
        registries.first()
    }

    Registry findRegistry(String name) {
        registries.find { it.name == name } ?: defaultRegistry
    }

    String getArch() {
        return arch
    }

    void setArch(String arch) {
        this.arch = arch
    }

    List<RegistryConfiguration> getRegistries() {
        return registries
    }

    void setRegistries(List<RegistryConfiguration> registries) {
        this.registries = registries
    }

    @EachProperty(value = "registries", list = true)
    static class RegistryConfiguration implements Registry {

        @NotBlank
        String name

        @NotBlank
        String host

        @Nullable
        AuthConfiguration auth


        @ConfigurationProperties("auth")
        static class AuthConfiguration implements Auth{

            @NotBlank
            String username

            @NotBlank
            String password

            @NotBlank
            String url

            @NotBlank
            String service
        }

    }
}
