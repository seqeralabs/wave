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

package io.seqera.wave.service.scan

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.configuration.ScanEnabled
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Requires(bean = ScanEnabled)
@Requires(missingProperty = 'wave.build.k8s')
@CompileStatic
class DockerScanStrategy extends ScanStrategy {

    @Inject
    private ScanConfig scanConfig

    DockerScanStrategy(ScanConfig scanConfig) {
        this.scanConfig = scanConfig
    }

    @Override
    void scanContainer(String jobName, ScanEntry entry) {
        log.info("Launching container scan job: $jobName for entry: $entry}")
        // config (docker auth) file name
        final Path configFile = entry.configJson ? entry.workDir.resolve('config.json') : null
        // create the launch command
        final dockerCommand = dockerWrapper(jobName, entry.workDir, configFile, scanConfig.environment)
        final command = dockerCommand + scanConfig.scanImage + "-c" + trivyCommand(entry.containerImage, entry.workDir, entry.platform, scanConfig)

        //launch scanning
        log.debug("Container scan command: ${command.join(' ')}")
        final process = new ProcessBuilder()
                .command(command)
                .redirectErrorStream(true)
                .start()

        if( process.waitFor()!=0 ) {
            throw new IllegalStateException("Unable to launch scan container - exitCode=${process.exitValue()}; output=${process.text}")
        }
    }

    @Override
    void scanPlugin(String jobName, ScanEntry entry) {
        log.info("Launching plugin scan job: $jobName for entry: $entry}")
        // config (docker auth) file name
        final Path configFile = entry.configJson ? entry.workDir.resolve('config.json') : null
        // create the launch command
        final dockerCommand = dockerWrapper(jobName, entry.workDir, configFile, scanConfig.environment)
        final command = dockerCommand + scanConfig.scanPluginImage + "-c" + scanPluginCommand(entry.containerImage, entry.workDir, scanConfig, ScanType.Default)

        //launch scanning
        log.debug("Plugin scan command: ${command.join(' ')}")
        final process = new ProcessBuilder()
                .command(command)
                .redirectErrorStream(true)
                .start()

        if( process.waitFor()!=0 ) {
            throw new IllegalStateException("Unable to launch scan container - exitCode=${process.exitValue()}; output=${process.text}")
        }
    }

    protected List<String> dockerWrapper(String jobName, Path scanDir, Path credsFile, List<String> env) {

        final wrapper = ['docker','run']
        wrapper.add('--detach')
        wrapper.add('--name')
        wrapper.add(jobName)
        // reset the entrypoint
        wrapper.add('--entrypoint')
        wrapper.add('/bin/sh')
        // scan work dir
        wrapper.add('-w')
        wrapper.add(scanDir.toString())

        wrapper.add('-v')
        wrapper.add("$scanDir:$scanDir:rw".toString())

        // cache directory
        wrapper.add('-v')
        wrapper.add("${scanConfig.cacheDirectory}:${Trivy.CACHE_MOUNT_PATH}:rw".toString())

        if(credsFile) {
            wrapper.add('-v')
            wrapper.add("${credsFile}:${Trivy.CONFIG_MOUNT_PATH}:ro".toString())
        }

        if( env ) {
            for( String it : env ) {
                wrapper.add('-e')
                wrapper.add(it)
            }
        }

        return wrapper
    }
}
