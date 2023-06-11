package io.seqera.wave.configuration

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton
/**
 * Model Spack configuration
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString
@EqualsAndHashCode
@Singleton
@CompileStatic
class SpackConfig {

    @Nullable
    @Value('${wave.build.spack.cacheDirectory}')
    private String cacheDirectory

    @Nullable
    @Value('${wave.build.spack.cacheMountPath}')
    private String cacheMountPath

    @Nullable
    @Value('${wave.build.spack.secretKeyFile}')
    private String secretKeyFile

    @Nullable
    @Value('${wave.build.spack.secretMountPath}')
    private String secretMountPath

    @Value('${wave.build.spack.builderImage:`spack/ubuntu-jammy:v0.20.0`}')
    private String builderImage

    Path getCacheDirectory() {
        if( !cacheDirectory )
            throw new IllegalStateException("Missing Spack cacheDirectory configuration setting")
        return Path.of(cacheDirectory).toAbsolutePath().normalize()
    }

    String getCacheMountPath() {
        if( !cacheMountPath )
            throw new IllegalStateException("Missing Spack cacheMountPath configuration setting")
        return cacheMountPath
    }

    Path getSecretKeyFile() {
        if( !secretKeyFile )
            throw new IllegalStateException("Missing Spack secretKeyFile configuration setting")
        return Path.of(secretKeyFile).toAbsolutePath().normalize()
    }

    String getSecretMountPath() {
        if( !secretMountPath )
            throw new IllegalStateException("Missing Spack secretMountPath configuration setting")
        return secretMountPath
    }

    String getBuilderImage() {
        return builderImage
    }
}
