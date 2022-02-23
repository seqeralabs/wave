package io.seqera.auth

import io.seqera.config.Auth

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
class ConfigurableAuthProvider extends BaseAuthProvider{

    Auth configuration

    ConfigurableAuthProvider(Auth configuration) {
        this.configuration = configuration
    }

    protected String getUsername(){
        configuration.username
    }

    protected String getPassword(){
        configuration.password
    }

    protected String getAuthUrl(){
        configuration.url
    }

    protected String getService(){
        configuration.service
    }

}
