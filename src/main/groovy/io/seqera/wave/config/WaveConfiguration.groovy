package io.seqera.wave.config

import java.time.Duration
import javax.validation.constraints.NotNull

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Context
@ConfigurationProperties('wave')
interface WaveConfiguration {

    @NotNull
    String getArch()

    boolean isDebug()

    boolean isAllowAnonymous()

    @NotNull
    ServerConfiguration getServer()

    @NotNull
    @MountPathValidator
    BuildConfiguration getBuild()

    @ConfigurationProperties('server')
    interface ServerConfiguration {
        String getUrl()
    }

    @ConfigurationProperties('build')
    interface BuildConfiguration{

        boolean isDebug()

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

        @Value('${timeout:5m}')
        Duration getTimeout()

        K8sConfiguration getK8s()

        @ConfigurationProperties('k8s')
        interface K8sConfiguration {

            @NotNull
            String getNamespace()

            boolean isDebug()

            @StorageConfigValidator
            StorageConfiguration getStorage()

            String getContext()

            String getConfigPath()

            @ConfigurationProperties('storage')
            interface StorageConfiguration {

                Optional<String> getClaimName()

                Optional<String> getMountPath()
            }

        }
    }

}
