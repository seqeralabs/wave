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

package io.seqera.wave.service.builder

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.TimeUnit

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.util.RegHelper
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE
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

    @Override
    BuildResult build(BuildRequest req) {

        Path configFile = null
        // save docker config for creds
        if( req.configJson ) {
            configFile = req.workDir.resolve('config.json')
            Files.write(configFile, JsonOutput.prettyPrint(req.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
        }
        // save remote files for singularity
        if( req.configJson && req.formatSingularity()) {
            final remoteFile = req.workDir.resolve('singularity-remote.yaml')
            final content = RegHelper.singularityRemoteFile(req.targetImage)
            Files.write(remoteFile, content.bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            // set permissions 600 as required by Singularity
            Files.setPosixFilePermissions(configFile, Set.of(OWNER_READ, OWNER_WRITE))
            Files.setPosixFilePermissions(remoteFile, Set.of(OWNER_READ, OWNER_WRITE))
        }

        // command the docker build command
        final buildCmd= buildCmd(req, configFile)
        log.debug "Build run command: ${buildCmd.join(' ')}"
        // save docker cli for debugging purpose
        if( debug ) {
            Files.write(req.workDir.resolve('docker.sh'),
                    buildCmd.join(' ').bytes,
                    CREATE, WRITE, TRUNCATE_EXISTING)
        }
        
        final proc = new ProcessBuilder()
                .command(buildCmd)
                .directory(req.workDir.toFile())
                .redirectErrorStream(true)
                .start()

        final completed = proc.waitFor(buildConfig.buildTimeout.toSeconds(), TimeUnit.SECONDS)
        final stdout = proc.inputStream.text
        return BuildResult.completed(req.id, completed ? proc.exitValue() : -1, stdout, req.startTime)
    }

    protected List<String> buildCmd(BuildRequest req, Path credsFile) {
        final spack = req.isSpackBuild ? spackConfig : null

        final dockerCmd = req.formatDocker()
                ? cmdForKaniko( req.workDir, credsFile, spack, req.platform)
                : cmdForSingularity( req.workDir, credsFile, spack, req.platform)

        return dockerCmd + launchCmd(req)
    }

    protected List<String> cmdForKaniko(Path workDir, Path credsFile, SpackConfig spackConfig, ContainerPlatform platform ) {
        final wrapper = ['docker',
                         'run',
                         '--rm',
                         '-v', "$workDir:$workDir".toString()]

        if( credsFile ) {
            wrapper.add('-v')
            wrapper.add("$credsFile:/kaniko/.docker/config.json:ro".toString())
        }

        if( spackConfig ) {
            // secret file
            wrapper.add('-v')
            wrapper.add("${spackConfig.secretKeyFile}:${spackConfig.secretMountPath}:ro".toString())
        }

        if( platform ) {
            wrapper.add('--platform')
            wrapper.add(platform.toString())
        }
        // the container image to be used t
        wrapper.add( buildConfig.kanikoImage )
        // return it
        return wrapper
    }

    protected List<String> cmdForSingularity(Path workDir, Path credsFile, SpackConfig spackConfig, ContainerPlatform platform) {
        final wrapper = ['docker',
                         'run',
                         '--rm',
                         '--privileged',
                         "--entrypoint", '',
                         '-v', "$workDir:$workDir".toString()]

        if( credsFile ) {
            wrapper.add('-v')
            wrapper.add("$credsFile:/root/.singularity/docker-config.json:ro".toString())
            //
            wrapper.add('-v')
            wrapper.add("${credsFile.resolveSibling('singularity-remote.yaml')}:/root/.singularity/remote.yaml:ro".toString())
        }

        if( spackConfig ) {
            // secret file
            wrapper.add('-v')
            wrapper.add("${spackConfig.secretKeyFile}:${spackConfig.secretMountPath}:ro".toString())
        }

        if( platform ) {
            wrapper.add('--platform')
            wrapper.add(platform.toString())
        }

        wrapper.add(buildConfig.singularityImage(platform))
        return wrapper
    }
}
