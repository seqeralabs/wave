package io.seqera.auth

import io.seqera.config.Registry
import jakarta.inject.Singleton

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Singleton
class AuthFactory {

    DockerAuthProvider getProvider(Registry config){
        config.auth == null ? new SimpleAuthProvider() : ConfigurableAuthProvider.builder()
                .username(config.auth.username)
                .password(config.auth.password)
                .service(config.auth.service)
                .authUrl(config.auth.url)
                .build()
    }

}
