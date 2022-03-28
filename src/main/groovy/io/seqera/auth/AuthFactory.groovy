package io.seqera.auth

import io.seqera.config.Registry
import jakarta.inject.Singleton

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Singleton
class AuthFactory {

    DockerAuthProvider getProvider(Registry config){
        config.auth ? new ConfigurableAuthProvider(config.auth) : new SimpleAuthProvider()
    }

}
