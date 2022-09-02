package io.seqera.wave.config

import javax.validation.constraints.NotNull

import io.micronaut.context.annotation.ConfigurationProperties


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('wave.server')
interface ServerConfiguration {
    @NotNull
    String getUrl()
}
