package io.seqera.config

import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.bind.annotation.Bindable

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@EachProperty(value="towerreg.registries", list = true)
class RegistryConfiguration implements Registry{

    private String name

    @NotBlank
    private String host

    String authConfig

    private AuthConfiguration auth

    RegistryConfiguration(@Parameter Integer index) {
    }

    @Bindable
    String getName() {
        return name
    }

    void setName(String name) {
        this.name = name
    }

    @Bindable
    String getHost() {
        return host
    }

    void setHost(String host) {
        this.host = host
    }

    @Bindable
    AuthConfiguration getAuth() {
        return auth
    }

    void setAuth(AuthConfiguration auth) {
        this.auth = auth
    }

    @Override
    String toString() {
        return "Registry[name=$name; host=$host; auth=$auth]"
    }

}
