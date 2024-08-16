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
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.ScanConfig
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.builder.BuildConstants.FUSION_PREFIX
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

    @Inject
    BuildConfig buildConfig

    @Inject
    @Named('build-workspace')
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    DockerScanStrategy(ScanConfig scanConfig) {
        this.scanConfig = scanConfig
    }

    @Override
    ScanResult scanContainer(ScanRequest req) {
        log.info("Launching container scan for buildId: ${req.buildId} with scanId ${req.id}")
        final startTime = Instant.now()

        try {
            // create the scan dir
            try {
                Files.createDirectory(req.workDir)
            }
            catch (FileAlreadyExistsException e) {
                log.warn("Container scan directory already exists: $e")
            }

            // save the config file with docker auth credentials
            if( req.configJson ) {
                objectStorageOperations.upload(UploadRequest.fromBytes(req.configJson.bytes, "$req.s3Key/config.json".toString()))
            }

            // outfile file name
            final reportFile = req.workDir.resolve(Trivy.OUTPUT_FILE_NAME)
            // create the launch command 
            final dockerCommand = dockerWrapper(req)
            final trivyCommand = List.of(scanConfig.scanImage) + scanCommand(req.targetImage, reportFile, scanConfig)
            final command = dockerCommand + trivyCommand

            //launch scanning
            log.debug("Container scan command: ${command.join(' ')}")
            final process = new ProcessBuilder()
                    .command(command)
                    .redirectErrorStream(true)
                    .start()

            final exitCode = process.waitFor()
            if ( exitCode != 0 ) {
                log.warn("Container scan failed to scan container, it exited with code: ${exitCode} - cause: ${process.text}")
                return ScanResult.failure(req, startTime)
            }
            else{
                log.info("Container scan completed with id: ${req.id}")
                return ScanResult.success(req, startTime, TrivyResultProcessor.process(reportFile.text))
            }
        }
        catch (Throwable e){
            log.error("Container scan failed to scan container - cause: ${e.getMessage()}", e)
            return ScanResult.failure(req, startTime)
        }
    }

    protected List<String> dockerWrapper(ScanRequest request) {

        final wrapper = ['docker','run', '--rm']

        // scan work dir
        wrapper.add('-e')
        wrapper.add("TRIVY_WORKSPACE_DIR=$FUSION_PREFIX/$buildConfig.workspaceBucket/$request.s3Key".toString())

        // cache directory
        wrapper.add('-e')
        wrapper.add("TRIVY_CACHE_DIR=$FUSION_PREFIX/$buildConfig.workspaceBucket/.trivy-cache".toString())

        if(request.configJson) {
            wrapper.add('-e')
            wrapper.add("DOCKER_CONFIG=$FUSION_PREFIX/$buildConfig.workspaceBucket/$request.s3Key".toString())
        }

        return wrapper
    }
}
