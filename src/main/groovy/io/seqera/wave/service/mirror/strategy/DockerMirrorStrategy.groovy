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

package io.seqera.wave.service.mirror.strategy

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.MirrorConfig
import io.seqera.wave.configuration.MirrorEnabled
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.util.FusionHelper
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.aws.ObjectStorageOperationsFactory.BUILD_WORKSPACE
/**
 * Implements a container mirror runner based on Docker
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(bean = MirrorEnabled)
@Singleton
@CompileStatic
@Slf4j
class DockerMirrorStrategy extends MirrorStrategy {

    @Value('${wave.debug:false}')
    private Boolean debug

    @Inject
    private MirrorConfig mirrorConfig

    @Inject
    private BuildConfig buildConfig

    @Inject
    @Named(BUILD_WORKSPACE)
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    @Override
    void mirrorJob(String jobName, MirrorRequest request) {
        // command the docker build command
        final buildCmd = mirrorCmd(jobName, request.workDir, request.authJson)
        buildCmd.addAll( copyCommand(request) )
        log.debug "Container mirror command: ${buildCmd.join(' ')}"
        // save docker cli for debugging purpose
        if( debug ) {
            objectStorageOperations.upload(UploadRequest.fromBytes(buildCmd.join(' ').bytes, "$request.workDir/docker.sh".toString()))
        }

        final process = new ProcessBuilder()
                .command(buildCmd)
                .redirectErrorStream(true)
                .start()
        if( process.waitFor()!=0 ) {
            throw new IllegalStateException("Unable to launch mirror container job - exitCode=${process.exitValue()}; output=${process.text}")
        }
    }

    protected List<String> mirrorCmd(String name, String workDir, String credsFile ) {
        //checkout the documentation here to know more about these options https://github.com/moby/buildkit/blob/master/docs/rootless.md#docker
        final wrapper = ['docker',
                         'run',
                        '--detach',
                         '--name', name,
                         '-e',
                         "AWS_ACCESS_KEY_ID=${System.getenv('AWS_ACCESS_KEY_ID')}".toString(),
                         '-e',
                         "AWS_SECRET_ACCESS_KEY=${System.getenv('AWS_SECRET_ACCESS_KEY')}".toString()]

        if( credsFile ) {
            wrapper.add('-e')
            wrapper.add("DOCKER_CONFIG=${ FusionHelper.getFusionPath(buildConfig.workspaceBucketName, workDir)}".toString())

            wrapper.add("-e")
            wrapper.add("REGISTRY_AUTH_FILE=${ FusionHelper.getFusionPath(buildConfig.workspaceBucketName, workDir)}".toString())
        }

        // the container image to be used to build
        wrapper.add( mirrorConfig.skopeoImage )
        // return it
        return wrapper
    }
}
