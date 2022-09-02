package io.seqera.wave.config

import java.time.Duration
import javax.annotation.Nullable
import javax.validation.constraints.NotNull

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('wave.build')
interface BuildConfiguration{

    @Bindable(defaultValue = "false")
    Boolean getDebug()

    /**
     * docker image to use as base
     */
    @NotNull
    String getImage()


    /**
     * File system path there the dockerfile is save
     */
    @NotNull
    String getWorkspace()

    /**
     * The registry repository where the build image will be stored
     */
    @NotNull
    String getRepo()

    /**
     * The registry repository to use as cache
     */
    @NotNull
    String getCache()

    @Bindable(defaultValue = "5m")
    Duration getTimeout()

    @Nullable
    K8sConfiguration getK8s()

}
