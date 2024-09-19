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

package io.seqera.wave.service.mirror.impl


import java.nio.file.Files
import java.nio.file.Path

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.mirror.MirrorConfig
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.mirror.MirrorStrategy
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE

/**
 * Implements a container mirror runner based on Docker
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
@Slf4j
class DockerMirrorStrategy extends MirrorStrategy {

    @Value('${wave.debug:false}')
    private Boolean debug

    @Inject
    private MirrorConfig mirrorConfig

    @Override
    void mirrorJob(String jobName, MirrorRequest request) {
        Path configFile = null
        // create the work directory
        Files.createDirectories(request.workDir)

        // save docker config for creds
        if( request.authJson ) {
            configFile = request.workDir.resolve('config.json')
            Files.write(configFile, JsonOutput.prettyPrint(request.authJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
        }

        // command the docker build command
        final buildCmd = mirrorCmd(jobName, request.workDir, configFile)
        buildCmd.addAll( copyCommand(request) )
        log.debug "Container mirror command: ${buildCmd.join(' ')}"
        // save docker cli for debugging purpose
        if( debug ) {
            Files.write(request.workDir.resolve('docker.sh'),
                    buildCmd.join(' ').bytes,
                    CREATE, WRITE, TRUNCATE_EXISTING)
        }

        final process = new ProcessBuilder()
                .command(buildCmd)
                .directory(request.workDir.toFile())
                .redirectErrorStream(true)
                .start()
        if( process.waitFor()!=0 ) {
            throw new IllegalStateException("Unable to launch mirror container job - exitCode=${process.exitValue()}; output=${process.text}")
        }
    }

    protected List<String> mirrorCmd(String name, Path workDir, Path credsFile ) {
        //checkout the documentation here to know more about these options https://github.com/moby/buildkit/blob/master/docs/rootless.md#docker
        final wrapper = ['docker',
                         'run',
                        '--detach',
                         '--name', name,
                         '-v', "$workDir:$workDir".toString() ]

        if( credsFile ) {
            wrapper.add('-v')
            wrapper.add("$credsFile:/tmp/config.json:ro".toString())

            wrapper.add("-e")
            wrapper.add("REGISTRY_AUTH_FILE=/tmp/config.json")
        }

        // the container image to be used to build
        wrapper.add( mirrorConfig.skopeoImage )
        // return it
        return wrapper
    }
}
