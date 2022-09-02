package io.seqera.wave.config

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.bind.annotation.Bindable


/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ConfigurationProperties('wave.build.k8s.storage')
interface StorageConfiguration {

    @Bindable(defaultValue = "")
    String getClaimName()

    @Bindable(defaultValue = "")
    String getMountPath()
}
