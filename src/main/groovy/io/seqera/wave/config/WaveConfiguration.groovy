package io.seqera.wave.config

import javax.annotation.Nullable
import javax.validation.constraints.NotNull

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import io.micronaut.core.bind.annotation.Bindable

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Context
@ConfigurationProperties('wave')
interface WaveConfiguration {

    @NotNull
    String getArch()

    @Bindable(defaultValue = "false")
    Boolean getDebug()

    @Bindable(defaultValue = "true")
    Boolean getAllowAnonymous()

    @NotNull
    ServerConfiguration getServer()

    @NotNull
    @MountPathValidator
    BuildConfiguration getBuild()


}
