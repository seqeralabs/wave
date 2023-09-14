/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
    @Value('${wave.build.spack.cacheS3Bucket}')
    private String cacheS3Bucket

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
    @Value('${wave.build.spack.runnerImage:`ubuntu:22.04`}')
    private String runnerImage

    String getCacheS3Bucket() {
        if( !cacheS3Bucket )
            throw new IllegalStateException("Missing Spack cacheMountPath configuration setting")
        return cacheS3Bucket
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

    String getRunnerImage() {
        return runnerImage
    }
}
