/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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

    /**
     * The s3 bucket where Spack cached binaries are stored
     */
    @Nullable
    @Value('${wave.build.spack.cacheBucket}')
    private String cacheBucket

    /**
     * The host path where the GPG key required by the Spack "buildcache" is located
     */
    @Nullable
    @Value('${wave.build.spack.secretKeyFile}')
    private String secretKeyFile

    /**
     * The container path where the GPG key required by the Spack "buildcache" is located
     */
    @Nullable
    @Value('${wave.build.spack.secretMountPath}')
    private String secretMountPath

    /**
     * The container image used for Spack builds
     */
    @Value('${wave.build.spack.builderImage:`spack/ubuntu-jammy:v0.20.0`}')
    private String builderImage

    /**
     * The container image used for Spack container
     */
    @Value('${wave.build.spack.runnerImage:`ubuntu:jammy`}')
    private String runnerImage

    String getCacheBucket() {
        if( !cacheBucket )
            throw new IllegalStateException("Missing Spack 'cacheBucket' configuration setting")
        return cacheBucket
    }

    Path getSecretKeyFile() {
        if( !secretKeyFile )
            throw new IllegalStateException("Missing Spack 'secretKeyFile' configuration setting")
        return Path.of(secretKeyFile).toAbsolutePath().normalize()
    }

    String getSecretMountPath() {
        if( !secretMountPath )
            throw new IllegalStateException("Missing Spack 'secretMountPath' configuration setting")
        return secretMountPath
    }

    String getBuilderImage() {
        return builderImage
    }

    String getRunnerImage() {
        return runnerImage
    }
}
