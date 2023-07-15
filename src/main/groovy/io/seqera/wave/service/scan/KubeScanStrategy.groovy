package io.seqera.wave.service.scan

import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import javax.annotation.Nullable

import groovy.json.JsonOutput
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiException
import io.kubernetes.client.openapi.models.V1Job
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE

import static io.seqera.wave.util.K8sHelper.getSelectorLabel

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

    @Value('${wave.scan.timeout:5m}')
    Duration scanTimeout

    @Property(name='wave.scan.k8s.node-selector')
    @Nullable
    private Map<String, String> nodeSelectorMap

    private final K8sService k8sService

    private final ScanConfig scanConfig

    KubeScanStrategy(K8sService k8sService, ScanConfig scanConfig) {
        this.k8sService = k8sService
        this.scanConfig = scanConfig
    }

    @Override
    ScanResult scanContainer(String scannerImage, BuildRequest req) {
        log.info("Launching container scan for buildId: ${req.id}")
        final startTime = Instant.now()

        final podName = podName(req)
        try{
            // create the scan dir
            try {
                Files.createDirectory(req.scanDir)
            }
            catch (FileAlreadyExistsException e) {
                log.warn("Container scan directory already exists: $e")
            }

            // save the config file with docker auth credentials
            Path configFile = null
            if( req.configJson ) {
                configFile = req.scanDir.resolve('config.json')
                Files.write(configFile, JsonOutput.prettyPrint(req.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            }

            final reportFile = req.scanDir.resolve(Trivy.OUTPUT_FILE_NAME)

            V1Job job
            final trivyCommand = scanCommand(req.targetImage, reportFile)
            final selector= getSelectorLabel(req.platform, nodeSelectorMap)
            final pod = k8sService.scanContainer(podName, scannerImage, trivyCommand, req.scanDir, configFile, scanConfig, selector)
            final terminated = k8sService.waitPod(pod, scanTimeout.toMillis())
            if( terminated ) {
                log.info("Container scan completed for buildId: ${req.id}")
                return ScanResult.success(req.id, startTime, TrivyResultProcessor.process(reportFile.text))
            }
            else{
                log.info("Container scan failed for buildId: ${req.id}")
                return ScanResult.failure(req.id, startTime, null)
            }
        }
        catch (ApiException e) {
            throw new BadRequestException("Unexpected scan failure: ${e.responseBody}", e)
        }
        catch (Exception e){
            log.warn("Error creating scan pod: ${e.getMessage()}", e)
            return ScanResult.failure(req.id, startTime, null)
        }
        finally {
            cleanup(podName)
        }
    }

    private String podName(BuildRequest req) {
        return "scan-${req.job}"
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
