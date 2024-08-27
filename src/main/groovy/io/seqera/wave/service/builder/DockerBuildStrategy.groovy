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

package io.seqera.wave.service.builder

import java.util.concurrent.TimeUnit

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.core.RegistryProxyService
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.builder.BuildConstants.FUSION_PREFIX
/**
 *  Build a container image using a Docker CLI tool
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class DockerBuildStrategy extends BuildStrategy {

    @Value('${wave.debug:false}')
    Boolean debug

    @Inject
    SpackConfig spackConfig

    @Inject
    BuildConfig buildConfig

    @Inject
    RegistryProxyService proxyService

    @Inject
    @Named('build-workspace')
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    @Override
    BuildResult build(BuildRequest req) {

        // command the docker build command
        final buildCmd= buildCmd(req)
        log.debug "Build run command: ${buildCmd.join(' ')}"
        // save docker cli for debugging purpose
        if( debug ) {
            objectStorageOperations.upload(UploadRequest.fromBytes(buildCmd.join(' ').bytes, "$req.s3Key/docker.sh".toString()))
        }
        
        final builder = new ProcessBuilder()
                .command(buildCmd)
                .redirectErrorStream(true)
        //this is to run it in windows
                .redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)

        def proc = builder.start()

        final timeout = req.maxDuration ?: buildConfig.defaultTimeout
        final completed = proc.waitFor(timeout.toSeconds(), TimeUnit.SECONDS)
        final stdout = proc.inputStream.text
        if( completed ) {
            final digest = proc.exitValue()==0 ? proxyService.getImageDigest(req, true) : null
            return BuildResult.completed(req.buildId, proc.exitValue(), stdout, req.startTime, digest)
        }
        else {
            return BuildResult.failed(req.buildId, stdout, req.startTime)
        }
    }

    protected List<String> buildCmd(BuildRequest req) {
        final dockerCmd = req.formatDocker()
                ? cmdForBuildkit( req)
                : cmdForSingularity(req)

        return dockerCmd + launchCmd(req)
    }

    protected List<String> cmdForBuildkit(BuildRequest req) {
        //checkout the documentation here to know more about these options https://github.com/moby/buildkit/blob/master/docs/rootless.md#docker
        final wrapper = ['docker',
                         'run',
                         '--rm',
                         '--privileged',
                         '-e',
                         "AWS_ACCESS_KEY_ID=${System.getenv('AWS_ACCESS_KEY_ID')}".toString(),
                         '-e',
                         "AWS_SECRET_ACCESS_KEY=${System.getenv('AWS_SECRET_ACCESS_KEY')}".toString()]

        if( req.configJson ) {
                wrapper.add('-e')
                wrapper.add("DOCKER_CONFIG=$FUSION_PREFIX/$buildConfig.workspaceBucket/$req.s3Key".toString())
        }

        if( req.platform ) {
            wrapper.add('--platform')
            wrapper.add(req.platform.toString())
        }

        // the container image to be used to build
        wrapper.add( buildConfig.buildkitImage )
        // return it
        return wrapper
    }

    protected List<String> cmdForSingularity(BuildRequest req) {
        final wrapper = ['docker',
                         'run',
                         '--rm',
                         '--privileged',
                         '-e',
                         "AWS_ACCESS_KEY_ID=${System.getenv('AWS_ACCESS_KEY_ID')}".toString(),
                         '-e',
                         "AWS_SECRET_ACCESS_KEY=${System.getenv('AWS_SECRET_ACCESS_KEY')}".toString()]

        if( req.platform ) {
            wrapper.add('--platform')
            wrapper.add(req.platform.toString())
        }

        wrapper.add(buildConfig.singularityImage(req.platform))
        return wrapper
    }
}
