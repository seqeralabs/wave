package io.seqera.wave.config

import java.nio.file.Path

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext
import jakarta.inject.Singleton


/**
 * A factory of Validators
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Factory
@CompileStatic
class WaveConfigurationFactory {

    @Singleton
    @Requires(property = 'wave.build.k8s.storage.mountPath')
    ConstraintValidator<MountPathValidator, BuildConfiguration>mountPathConstraintValidator(){
        ConstraintValidator<MountPathValidator, BuildConfiguration> ret = { BuildConfiguration value, annotationType, ConstraintValidatorContext context ->
            K8sConfiguration k8sConfiguration = value.k8s
            if( k8sConfiguration.storage.mountPath ) {
                if( !Path.of(value.workspace).startsWith(k8sConfiguration.storage.mountPath) ) {
                    context.messageTemplate "Build workspace should be a sub-directory of 'wave.build.k8s.storage.mountPath' - offending value: '$value.workspace' - expected value: '$k8sConfiguration.storage.mountPath'"
                    return false
                }
            }
            true
        }
        ret
    }

    @Singleton
    @Requires(property = 'wave.build.k8s.storage')
    ConstraintValidator<StorageConfigValidator, StorageConfiguration>storageConfigurationValidator(){
        ConstraintValidator<StorageConfigValidator, StorageConfiguration> ret = { StorageConfiguration value, annotationType, ConstraintValidatorContext context ->
            if( value.claimName && !value.mountPath ) {
                context.messageTemplate"Missing 'wave.build.k8s.storage.mountPath' configuration attribute"
                return false
            }
            if( !value.claimName && value.mountPath ) {
                context.messageTemplate"Missing 'wave.build.k8s.storage.claimName' configuration attribute"
                return false
            }
            true
        }
        ret
    }
}
