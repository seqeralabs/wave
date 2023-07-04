package io.seqera.wave.service.scan

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import javax.annotation.Nullable

import groovy.json.JsonOutput
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobBuilder
import io.kubernetes.client.util.Config
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.configuration.ContainerScanConfig
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Singleton
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE

/**
 * Implements ContainerScanStrategy for Kubernetes
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Primary
@Requires(property = 'wave.build.k8s')
@Singleton
@CompileStatic
class KubernetesContainerScanStrategy extends ContainerScanStrategy{
    @Value('${wave.scan.timeout:5m}')
    Duration scanTimeout

    @Property(name='wave.scan.k8s.node-selector')
    @Nullable
    private Map<String, String> nodeSelectorMap

    private  final K8sService k8sService

    private final ContainerScanConfig containerScanConfig

    KubernetesContainerScanStrategy(K8sService k8sService, ContainerScanConfig containerScanConfig) {
        this.k8sService = k8sService
        this.containerScanConfig = containerScanConfig
    }

    @Override
    ScanResult scanContainer(String containerScanner, BuildRequest buildRequest) {
        log.info("Launching container scan for buildId: "+buildRequest.id)

        Instant startTime = Instant.now()

        Path configFile = null
        Path scanDir = null
        try{
            if( buildRequest.configJson ) {
                scanDir = Files.createDirectories(Path.of(containerScanConfig.workspace))
                configFile = scanDir.resolve('config.json').toAbsolutePath()
                Files.write(configFile, JsonOutput.prettyPrint(buildRequest.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            }
        V1Job job
            def trivyCommand = trivyWrapper(buildRequest.targetImage)
            final trivyCredsPath = '/root/.docker/config.json'
            final name = podName(buildRequest)
            final selector= k8sService.getSelectorLabel(buildRequest.platform, nodeSelectorMap)
            final pod = k8sService.buildContainer(name, containerScanner, trivyCommand, null, scanDir, configFile, null, selector,trivyCredsPath)
            final terminated = k8sService.waitPod(pod, scanTimeout.toMillis())
            final stdout = k8sService.logsPod(name)
            if( terminated ) {
                return ScanResult.success(buildRequest.id, startTime, TrivyResultProcessor.process(stdout))
            }else{
                return ScanResult.failure(buildRequest.id, startTime, null)
            }
        }catch (Exception e){
            log.warn("Error in creating scan pod in kubernetes : ${e.getMessage()}", e)
            return ScanResult.failure(buildRequest.id, startTime, null)
        }
    }
    private String podName(BuildRequest req) {
        return "scan-${req.job}"
    }
}
