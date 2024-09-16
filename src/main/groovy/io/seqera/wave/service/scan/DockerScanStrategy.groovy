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

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.ScanConfig
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE
/**
 * Implements ScanStrategy for Docker
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@Requires(missingProperty = 'wave.build.k8s')
@CompileStatic
class DockerScanStrategy extends ScanStrategy {

    @Inject
    private ScanConfig scanConfig

    DockerScanStrategy(ScanConfig scanConfig) {
        this.scanConfig = scanConfig
    }

    @Override
    void scanContainer(String jobName, ScanRequest req) {
        log.info("Launching container scan for buildId: ${req.buildId} with scanId ${req.id}")

        // create the scan dir
        try {
            Files.createDirectory(req.workDir)
        }
        catch (FileAlreadyExistsException e) {
            log.warn("Container scan directory already exists: $e")
        }

        // save the config file with docker auth credentials
        Path configFile = null
        if( req.configJson ) {
            configFile = req.workDir.resolve('config.json')
            Files.write(configFile, JsonOutput.prettyPrint(req.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
        }

        // outfile file name
        final reportFile = req.workDir.resolve(Trivy.OUTPUT_FILE_NAME)
        // create the launch command
        final dockerCommand = dockerWrapper(jobName, req.workDir, configFile)
        final trivyCommand = List.of(scanConfig.scanImage) + scanCommand(req.targetImage, reportFile, scanConfig)
        final command = dockerCommand + trivyCommand

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

    protected List<String> dockerWrapper(String jobName, Path scanDir, Path credsFile) {

        final wrapper = ['docker','run']
        wrapper.add('--detach')
        wrapper.add('--name')
        wrapper.add(jobName)

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


        return wrapper
    }
}
