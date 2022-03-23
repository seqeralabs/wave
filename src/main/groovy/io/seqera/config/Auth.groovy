package io.seqera.config

import javax.validation.constraints.NotBlank
import io.micronaut.context.annotation.ConfigurationInject
import io.seqera.util.StringUtils

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
class Auth {

    @ConfigurationInject
    Auth( @NotBlank String username, @NotBlank String password, @NotBlank String url, @NotBlank String service){
        this.username=username
        this.password=password
        this.url=url
        this.service=service
    }

    private String username
    private String password
    private String url
    private String service

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

    @Override
    String toString() {
        "Auth[service=$service; username=$username; password=${StringUtils.redact(password)}; url=$url]"
    }
}
