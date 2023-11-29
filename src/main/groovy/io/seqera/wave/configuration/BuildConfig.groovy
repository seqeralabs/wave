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

import java.time.Duration
import javax.annotation.Nullable
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.core.ContainerPlatform
import jakarta.inject.Singleton
/**
 * Container build service configuration
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
@Slf4j
class BuildConfig {

    @Value('${wave.build.kaniko-image}')
    String kanikoImage

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
    @Value('${wave.build.public}')
    String defaultPublicRepository

    /**
     * File system path there the dockerfile is save
     */
    @Value('${wave.build.workspace}')
    String buildWorkspace

    @Value('${wave.build.status.delay}')
    Duration statusDelay

    @Value('${wave.build.timeout:5m}')
    Duration buildTimeout

    @Value('${wave.build.status.duration}')
    Duration statusDuration

    @Value('${wave.build.cleanup}')
    @Nullable
    String cleanup

    @Value('${wave.build.compress-caching:true}')
    Boolean compressCaching = true

    @PostConstruct
    private void init() {
        log.debug("Builder config: " +
                "kaniko name: ${kanikoImage}; " +
                "singularity image: ${singularityImage}; " +
                "singularity image amr64: ${singularityImageArm64}; " +
                "default build repository: ${defaultBuildRepository}; " +
                "default build cache repository: ${defaultCacheRepository};" +
                "default build public repository: ${defaultPublicRepository};" +
                "workspace: ${buildWorkspace};" +
                "timeout: ${buildTimeout}; " +
                "status-delay: ${statusDelay}" +
                "status-duration: ${statusDuration};" +
                "cleanup: ${cleanup};" +
                "compress-caching: $compressCaching;")
    }

    String getSingularityImage(ContainerPlatform containerPlatform){
        return containerPlatform.arch == "arm64"
                ? getSingularityImageArm64()
                : singularityImage
    }

    String getSingularityImageArm64(){
        return singularityImageArm64 ?: singularityImage + "-arm64"
    }
}
