/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.soci.strategy

import java.nio.file.Files
import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.configuration.SociEnabled
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Run the {@code soci-indexer} job as a local Docker container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Requires(bean = SociEnabled)
@CompileStatic
class DockerSociIndexStrategy extends SociIndexStrategy {

    @Inject
    private SociConfig config

    @Override
    void launchCommand(String jobName, Path workDir, List<String> command) {
        final cli = dockerCommand(jobName, workDir, command)
        log.debug "SOCI job command: ${cli.join(' ')}"
        final process = new ProcessBuilder()
                .command(cli)
                .directory(workDir.toFile())
                .redirectErrorStream(true)
                .start()
        if( process.waitFor()!=0 ) {
            throw new IllegalStateException("Unable to launch SOCI index job - exitCode=${process.exitValue()}; output=${process.text}")
        }
    }

    protected List<String> dockerCommand(String jobName, Path workDir, List<String> command) {
        final Path configFile = workDir.resolve('config.json')
        final result = new ArrayList<String>(30)
        result.addAll(['docker', 'run', '--detach', '--name', jobName, '-v', "${workDir}:${workDir}:ro".toString()])
        if( Files.exists(configFile) ) {
            result.add('-v')
            result.add("${configFile}:${DOCKER_CONFIG_DIR}/config.json:ro".toString())
            result.add('-e')
            result.add("DOCKER_CONFIG=${DOCKER_CONFIG_DIR}".toString())
        }
        result.add(config.indexerImage)
        result.addAll(command)
        return result
    }
}
