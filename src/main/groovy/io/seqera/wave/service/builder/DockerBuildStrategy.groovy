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

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.BuildEnabled
import io.seqera.wave.core.RegistryProxyService
import io.seqera.wave.util.ContainerHelper
import io.seqera.wave.util.FusionHelper
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.aws.ObjectStorageOperationsFactory.BUILD_WORKSPACE
/**
 *  Build a container image using a Docker CLI tool
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
@Requires(bean = BuildEnabled)
class DockerBuildStrategy extends BuildStrategy {

    @Value('${wave.debug:false}')
    Boolean debug

    @Inject
    BuildConfig buildConfig

    @Inject
    RegistryProxyService proxyService

    @Inject
    @Named(BUILD_WORKSPACE)
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    @Override
    void build(String jobName, BuildRequest req) {

        // command the docker build command
        final buildCmd= buildCmd(jobName, req)
        log.debug "Build run command: ${buildCmd.join(' ')}"
        // save docker cli for debugging purpose
        if( debug ) {
            objectStorageOperations.upload(UploadRequest.fromBytes(buildCmd.join(' ').bytes, "$req.workDir/docker.sh".toString()))
        }
        
        final builder = new ProcessBuilder()
            .command(buildCmd)
        //this is to run it in windows
            .redirectError(ProcessBuilder.Redirect.INHERIT)
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT)

        def process = builder.start()

        if( process.waitFor()!=0 ) {
            throw new IllegalStateException("Unable to launch build container - exitCode=${process.exitValue()}; output=${process.text}")
        }
    }

    protected List<String> buildCmd(String jobName, BuildRequest req, Map<String, String> env = System.getenv()) {

        final dockerCmd = req.formatDocker()
                ? cmdForBuildkit(jobName, req, env)
                : cmdForSingularity(jobName, req, env)

        return dockerCmd + launchCmd(req)
    }

    protected List<String> cmdForBuildkit(String name, BuildRequest req, Map<String, String> env = System.getenv()) {
        //checkout the documentation here to know more about these options https://github.com/moby/buildkit/blob/master/docs/rootless.md#docker
        final wrapper = ['docker',
                         'run',
                         '--detach',
                         '--name', name,
                         '--privileged',
                         '-e',
                         'TMPDIR=/tmp']
        wrapper.addAll(ContainerHelper.getAWSAuthEnvVars(env))
        if( req.configJson ) {
            wrapper.add('-e')
            wrapper.add("DOCKER_CONFIG=${FusionHelper.getFusionPath(buildConfig.workspaceBucket, req.workDir)}".toString())
        }

        if( req.platform ) {
            wrapper.add('--platform')
            wrapper.add(req.platform.toString())
        }

        // the container image to be used to build
        wrapper.add( getBuildImage(req) )
        // return it
        return wrapper
    }

    protected List<String> cmdForSingularity(String name, BuildRequest req, Map<String, String> env = System.getenv() ) {
        final wrapper = ['docker',
                         'run',
                         '--detach',
                         '--name', name,
                         '--privileged']
            wrapper.addAll(ContainerHelper.getAWSAuthEnvVars(env))

        if( req.platform ) {
            wrapper.add('--platform')
            wrapper.add(req.platform.toString())
        }

        wrapper.add(buildConfig.singularityImage)
        return wrapper
    }
}
