package io.seqera.config

import java.time.Duration
import javax.annotation.Nullable
import javax.validation.constraints.NotBlank
import javax.validation.constraints.Size

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Parameter
import io.micronaut.context.annotation.Requires
import io.seqera.util.StringUtils

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 * */
@Context
@ConfigurationProperties("towerreg")
@Factory
class DefaultConfiguration implements TowerConfiguration{

    @NotBlank
    private String arch

    private String layerPath

    String getArch() {
        return arch
    }

    @Override
    String getLayerPath() {
        return layerPath
    }

    @EachProperty(value = "registries")
    static class RegistryConfiguration {

        RegistryConfiguration(@Parameter String name){
            this.name = name
        }
        String name

        @NotBlank
        String host

        @Nullable
        AuthConfiguration auth


        @ConfigurationProperties("auth")
        static class AuthConfiguration implements Auth{

            @NotBlank
            String username

            @NotBlank
            String password

            @NotBlank
            String url

            @NotBlank
            String service


            @Override
            String toString() {
                return "AuthConfiguration{" +
                        "username='" + username + '\'' +
                        ", password='" + StringUtils.redact(password) + '\'' +
                        ", url='" + url + '\'' +
                        ", service='" + service + '\'' +
                        '}';
            }
        }

    }

    @EachBean(RegistryConfiguration)
    RegistryBean registryBean(RegistryConfiguration configuration){
        RegistryBean.builder().name(configuration.name).host(configuration.host).auth(configuration.auth).build()
    }

    @ConfigurationProperties("storage")
    static class StorageConfigurationImpl implements StorageConfiguration{
        int maximumSize = 1000
        Duration expireAfter = Duration.ofMinutes(60)
    }

    @Requires(property = "towerreg.storage.file.enabled", value = "true")
    @ConfigurationProperties("storage.file")
    static class FileConfigurationImpl implements FileStorageConfiguration{
        @NotBlank
        String path
        boolean enabled = false
        boolean storeRemotes=false
    }

    @Requires(property = "towerreg.storage.s3.enabled", value = "true")
    @ConfigurationProperties("storage.s3")
    static class S3ConfigurationImpl implements S3StorageConfiguration{
        Optional<String> endpoint
        @NotBlank
        String bucket
        boolean enabled = false
        boolean storeRemotes=false
    }

}
