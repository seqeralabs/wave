package io.seqera.docker

import io.seqera.RegConfiguration

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
class ConfigurableAuthProvider extends BaseAuthProvider{

    RegConfiguration configuration

    ConfigurableAuthProvider(RegConfiguration configuration) {
        this.configuration = configuration
    }

    protected String getUsername(){
        configuration.username
    }

    protected String getPassword(){
        configuration.password
    }

    protected String getAuthUrl(){
        configuration.authUrl
    }

    protected String getService(){
        configuration.authService
    }

}
