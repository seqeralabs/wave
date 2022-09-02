package io.seqera.wave.config

import javax.validation.constraints.NotNull

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Requires
import io.micronaut.core.bind.annotation.Bindable

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */

@Requires(property = 'wave.build.k8s')
@ConfigurationProperties('wave.build.k8s')
interface K8sConfiguration {

    @NotNull
    String getNamespace()

    @Bindable(defaultValue = "false")
    Boolean getDebug()

    @StorageConfigValidator
    StorageConfiguration getStorage()

    String getContext()

    String getConfigPath()

}
