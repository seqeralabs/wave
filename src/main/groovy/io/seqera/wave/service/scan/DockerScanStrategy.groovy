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
            // save the config file with docker auth credentials
            if( req.configJson ) {
                objectStorageOperations.upload(UploadRequest.fromBytes(req.configJson.bytes, "$req.s3Key/config.json".toString()))
            }

            // create outfile file
            objectStorageOperations.upload(UploadRequest.fromBytes(new byte[0], "$req.s3Key/$Trivy.OUTPUT_FILE_NAME".toString()))
            final reportFile = "$FUSION_PREFIX/$buildConfig.workspaceBucket/$req.s3Key/$Trivy.OUTPUT_FILE_NAME"
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
                def scanReportFile =  objectStorageOperations.retrieve("$req.s3Key/$Trivy.OUTPUT_FILE_NAME".toString())
                        .map { it.toStreamedFile().inputStream.text }.get()
                return ScanResult.success(req, startTime, TrivyResultProcessor.process(scanReportFile))
            }
        }
        catch (Throwable e){
            log.error("Container scan failed to scan container - cause: ${e.getMessage()}", e)
            return ScanResult.failure(req, startTime)
        }
    }

    protected List<String> dockerWrapper(ScanRequest req) {

        final wrapper = ['docker',
                         'run',
                         '--privileged',
                         '-e',
                         "AWS_ACCESS_KEY_ID=${System.getenv('AWS_ACCESS_KEY_ID')}".toString(),
                         '-e',
                         "AWS_SECRET_ACCESS_KEY=${System.getenv('AWS_SECRET_ACCESS_KEY')}".toString()]

        // scan work dir
        wrapper.add('-e')
        wrapper.add("TRIVY_WORKSPACE_DIR=$FUSION_PREFIX/$buildConfig.workspaceBucket/$req.s3Key".toString())

        // cache directory
        wrapper.add('-e')
        wrapper.add("TRIVY_CACHE_DIR=$FUSION_PREFIX/$buildConfig.workspaceBucket/.trivy-cache".toString())

        if(req.configJson) {
            wrapper.add('-e')
            wrapper.add("DOCKER_CONFIG=$FUSION_PREFIX/$buildConfig.workspaceBucket/$req.s3Key".toString())
        }

        return wrapper
    }
}
