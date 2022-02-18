package io.seqera.docker

import io.seqera.config.Auth
import io.seqera.config.Registry
import io.seqera.config.TowerConfiguration

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
class AuthFactory {

    DockerAuthProvider getProvider(Registry config){
        new ConfigurableAuthProvider(config.auth)
    }

}
