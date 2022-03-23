package io.seqera.config

import javax.validation.constraints.NotBlank

import io.micronaut.context.annotation.ConfigurationInject

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
class Registry {

    @ConfigurationInject
    Registry( @NotBlank String name, @NotBlank String host, @NotBlank Auth auth){
        this.name=name
        this.host=host
        this.auth=auth
    }

    private String name
    private String host
    private Auth auth

    String getName() {
        return name
    }

    String getHost() {
        return host
    }

    Auth getAuth() {
        return auth
    }

    @Override
    String toString() {
        return "Registry[name=$name; host=$host; auth=$auth]"
    }
}
