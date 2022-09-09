package io.seqera.wave.configuration

import io.micronaut.context.annotation.ConfigurationProperties


/**
 * Model Redis server configuration settings
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('redis')
interface RedisConfig {

    String getUri()

}
