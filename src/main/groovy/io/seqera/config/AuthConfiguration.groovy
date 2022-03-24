package io.seqera.config

import javax.validation.constraints.NotBlank

import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.bind.annotation.Bindable

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@EachProperty("towerreg.auths")
class AuthConfiguration implements Auth{

    @NotBlank
    String name
    @NotBlank
    private String username
    @NotBlank
    private String password
    @NotBlank
    private String url
    @NotBlank
    private String service

    AuthConfiguration(@Parameter String name) {
        this.name = name
    }

    String getName() {
        return name
    }

    String getUsername() {
        return username
    }

    String getPassword() {
        return password
    }

    String getUrl() {
        return url
    }

    String getService() {
        return service
    }

}
