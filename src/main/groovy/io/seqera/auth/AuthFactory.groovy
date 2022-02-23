package io.seqera.auth


import io.seqera.config.Registry

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
class AuthFactory {

    DockerAuthProvider getProvider(Registry config){
        new ConfigurableAuthProvider(config.auth)
    }

}
