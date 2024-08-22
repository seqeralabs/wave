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
import java.time.Instant
import io.micronaut.core.annotation.Nullable

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiException
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.objectstorage.ObjectStorageOperations
import io.micronaut.objectstorage.request.UploadRequest
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton
import static io.seqera.wave.service.builder.BuildConstants.FUSION_PREFIX
import static io.seqera.wave.util.K8sHelper.getSelectorLabel
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE
/**
 * Implements ScanStrategy for Kubernetes
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Primary
@Requires(property = 'wave.build.k8s')
@Singleton
@CompileStatic
class KubeScanStrategy extends ScanStrategy {

    @Property(name='wave.build.k8s.node-selector')
    @Nullable
    private Map<String, String> nodeSelectorMap

    @Inject
    BuildConfig buildConfig

    @Inject
    @Named('build-workspace')
    private ObjectStorageOperations<?, ?, ?> objectStorageOperations

    private final K8sService k8sService

    private final ScanConfig scanConfig

    KubeScanStrategy(K8sService k8sService, ScanConfig scanConfig) {
        this.k8sService = k8sService
        this.scanConfig = scanConfig
    }

    @Override
    ScanResult scanContainer(ScanRequest req) {
        log.info("Launching container scan for buildId: ${req.buildId} with scanId ${req.id}")
        final startTime = Instant.now()

        final podName = "scan-${req.id}"
        try{
            // save the config file with docker auth credentials
            if( req.configJson ) {
                objectStorageOperations.upload(UploadRequest.fromBytes(req.configJson.bytes, "$req.s3Key/config.json".toString()))
            }

            // outfile file name
            final reportFile = "$FUSION_PREFIX/$buildConfig.workspaceBucket/$req.s3Key/$Trivy.OUTPUT_FILE_NAME"
            final trivyCommand = scanCommand(req.targetImage, reportFile, scanConfig)
            final selector= getSelectorLabel(req.platform, nodeSelectorMap)
            final pod = k8sService.scanContainer(podName, scanConfig.scanImage, trivyCommand, req.workDir, null, scanConfig, selector)
            final exitCode = k8sService.waitPodCompletion(pod, scanConfig.timeout.toMillis())
            if( exitCode==0 ) {
                log.info("Container scan completed for id: ${req.id}")
                def scanReportFile =  objectStorageOperations.retrieve("$req.s3Key/$Trivy.OUTPUT_FILE_NAME".toString()).get()
                scanReportFile = scanReportFile ? scanReportFile as String : null
                return ScanResult.success(req, startTime, TrivyResultProcessor.process(scanReportFile))
            }
            else{
                final stdout = k8sService.logsPod(pod)
                log.info("Container scan failed for scan id: ${req.id} - stdout: $stdout")
                return ScanResult.failure(req, startTime)
            }
        }
        catch (ApiException e) {
            throw new BadRequestException("Unexpected scan failure: ${e.responseBody}", e)
        }
        catch (Throwable e){
            log.error("Error while scanning with id: ${req.id} - cause: ${e.getMessage()}", e)
            return ScanResult.failure(req, startTime)
        }
        finally {
            cleanup(podName)
        }
    }

    void cleanup(String podName) {
        try {
            k8sService.deletePod(podName)
        }
        catch (Exception e) {
            log.warn ("Unable to delete pod=$podName - cause: ${e.message ?: e}", e)
        }
    }
}
