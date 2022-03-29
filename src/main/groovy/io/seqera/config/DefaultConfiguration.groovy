package io.seqera.config

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Context
@ConfigurationProperties("towerreg")
@Factory
class DefaultConfiguration {

    @NotBlank
    private String arch

    String getArch() {
        return arch
    }

    @EachProperty(value = "registries", list = true)
    static class RegistryConfiguration {

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

    @EachBean(RegistryConfiguration)
    RegistryBean registryBean(RegistryConfiguration configuration){
        RegistryBean.builder().name(configuration.name).host(configuration.host).auth(configuration.auth).build()
    }
}
