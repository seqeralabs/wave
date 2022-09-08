package io.seqera.wave.configuration

import io.micronaut.context.annotation.ConfigurationProperties


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('redis')
interface RedisConfiguration {

    String getUri()

}
