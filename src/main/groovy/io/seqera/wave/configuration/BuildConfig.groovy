/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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

import java.time.Duration
import javax.annotation.Nullable
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.ContainerPlatform
import jakarta.inject.Singleton
/**
 * Model Wave build config settings
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
@Slf4j
class BuildConfig {

    @Value('${wave.build.buildkit-image}')
    String buildkitImage

    @Value('${wave.build.singularity-image}')
    String singularityImage

    @Nullable
    @Value('${wave.build.singularity-image-arm64}')
    String singularityImageArm64

    @Value('${wave.build.repo}')
     String defaultBuildRepository

    @Value('${wave.build.cache}')
    String defaultCacheRepository

    @Nullable
    @Value('${wave.build.public-repo}')
    String defaultPublicRepository

    /**
     * File system path there the dockerfile is save
     */
    @Value('${wave.build.workspace}')
    String buildWorkspace

    @Value('${wave.build.status.delay}')
    Duration statusDelay

    @Value('${wave.build.timeout:5m}')
    Duration defaultTimeout

    @Value('${wave.build.trusted-timeout:10m}')
    Duration trustedTimeout

    @Value('${wave.build.status.duration}')
    Duration statusDuration

    @Nullable
    @Value('${wave.build.failure.duration}')
    Duration failureDuration

    Duration getFailureDuration() {
        return failureDuration ?: statusDelay.multipliedBy(10)
    }

    @Value('${wave.build.reserved-words:[]}')
    Set<String> reservedWords

    @Value('${wave.build.record.duration:5d}')
    Duration recordDuration

    @Value('${wave.build.oci-mediatypes:true}')
    Boolean ociMediatypes

    //check here for other options https://github.com/moby/buildkit?tab=readme-ov-file#registry-push-image-and-cache-separately
    @Value('${wave.build.compression:gzip}')
    String compression

    @Value('${wave.build.force-compression:false}')
    Boolean forceCompression

    /**
     * The number of times a build job should be retries. Since failures are expected due to
     * invalid Dockerfile or Conda environment, retry is disabled.
     */
    @Value('${wave.build.retry-attempts:0}')
    int retryAttempts

    @Value('${wave.build.max_conda_file_size:100000}')
    int maxCondaFileSize

    @Value('${wave.build.max_container_file_size:100000}')
    int maxContainerFileSize

    @PostConstruct
    private void init() {
        log.info("Builder config: " +
                "buildkit-image=${buildkitImage}; " +
                "singularity-image=${singularityImage}; " +
                "singularity-image-amr64=${singularityImageArm64}; " +
                "default-build-repository=${defaultBuildRepository}; " +
                "default-cache-repository=${defaultCacheRepository}; " +
                "default-public-repository=${defaultPublicRepository}; " +
                "build-workspace=${buildWorkspace}; " +
                "build-timeout=${defaultTimeout}; " +
                "build-trusted-timeout=${trustedTimeout}; " +
                "status-delay=${statusDelay}; " +
                "status-duration=${statusDuration}; " +
                "failure-duration=${getFailureDuration()}; " +
                "record-duration=${recordDuration}; " +
                "oci-mediatypes=${ociMediatypes}; " +
                "compression=${compression}; " +
                "force-compression=${forceCompression}; " +
                "retry-attempts=${retryAttempts}")
        // minimal validation
        if( trustedTimeout < defaultTimeout ) {
            log.warn "Trusted build timeout should be longer than default timeout - check configuration setting 'wave.build.trusted-timeout'"
        }
    }

    String singularityImage(ContainerPlatform containerPlatform){
        return containerPlatform.arch == "arm64"
                ? getSingularityImageArm64()
                : singularityImage
    }

    String getSingularityImageArm64(){
        return singularityImageArm64 ?: singularityImage + "-arm64"
    }

    Duration buildMaxDuration(SubmitContainerTokenRequest request) {
        // build max duration - when the user identity is provided and freeze is enabled
        // use `trustedTimeout` which is expected to be longer than `defaultTimeout`
        return request.towerAccessToken && request.freeze && trustedTimeout>defaultTimeout
                ? trustedTimeout
                : defaultTimeout
    }
}
