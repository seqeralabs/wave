package io.seqera.wave.config

import java.nio.file.Path

import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton


/**
 * A factory of Validators
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Factory
class WaveConfigurationFactory {

    @Singleton
    @Requires(property = 'wave.build.k8s.storage.mountPath')
    ConstraintValidator<MountPathValidator, WaveConfiguration.BuildConfiguration>mountPathConstraintValidator(){
        (value, annotationType, context)->{
            WaveConfiguration.BuildConfiguration.K8sConfiguration k8sConfiguration = value.k8s
            if( k8sConfiguration.storage.mountPath.isPresent() ) {
                if( !Path.of(value.workspace).startsWith(k8sConfiguration.storage.mountPath.get()) ) {
                    context.messageTemplate "Build workspace should be a sub-directory of 'wave.build.k8s.storage.mountPath' - offending value: '$value.workspace' - expected value: '$k8sConfiguration.storage.storageMountPath'"
                    return false
                }
            }
            true
        }
    }

    @Singleton
    @Requires(property = 'wave.build.k8s.storage')
    ConstraintValidator<StorageConfigValidator, WaveConfiguration.BuildConfiguration.K8sConfiguration.StorageConfiguration>storageConfigurationValidator(){
        (value, annotationType, context)->{
            if( value.claimName.isPresent() && !value.mountPath.isPresent() ) {
                context.messageTemplate"Missing 'wave.build.k8s.storage.mountPath' configuration attribute"
                return false
            }
            if( !value.claimName.isPresent() && value.mountPath.isPresent() ) {
                context.messageTemplate"Missing 'wave.build.k8s.storage.claimName' configuration attribute"
                return false
            }
            true
        }
    }
}
