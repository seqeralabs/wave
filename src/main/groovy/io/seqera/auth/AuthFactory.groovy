package io.seqera.auth

import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.seqera.config.RegistryBean

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Deprecated
@Factory
class AuthFactory {

    @EachBean(RegistryBean)
    protected DockerAuthProvider dockerAuthProvider(RegistryBean config){
        config.auth == null ? new SimpleAuthProvider() : ConfigurableAuthProvider.builder()
                .username(config.auth.username)
                .password(config.auth.password)
                .service(config.auth.service)
                .authUrl(config.auth.url)
                .build()
    }

}
