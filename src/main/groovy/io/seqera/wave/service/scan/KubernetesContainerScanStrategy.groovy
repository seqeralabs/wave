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
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.exception.BadRequestException
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
            final selector= getSelectorLabel(buildRequest.platform, nodeSelectorMap)
            final pod = k8sService.scanContainer(name, containerScanner, trivyCommand, scanDir, configFile, selector,trivyCredsPath)
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
    /**
     * Given the requested container platform and collection of node selector labels find the best
     * matching label
     *
     * @param platform
     *      The requested container platform e.g. {@code linux/amd64}
     * @param nodeSelectors
     *      A map that associate the platform architecture with a corresponding node selector label
     * @return
     *      A {@link Map} object representing a kubernetes label to be used as node selector for the specified
     *      platform or a empty map when there's no matching
     */
    Map<String,String> getSelectorLabel(ContainerPlatform platform, Map<String,String> nodeSelectors) {
        if( !nodeSelectors )
            return Collections.emptyMap()

        final key = platform.toString()
        if( nodeSelectors.containsKey(key) ) {
            return toLabelMap(nodeSelectors[key])
        }

        final allKeys = nodeSelectors.keySet().sort( it -> it.size() ).reverse()
        for( String it : allKeys ) {
            if( ContainerPlatform.of(it) == platform ) {
                return toLabelMap(nodeSelectors[it])
            }
        }

        throw new BadRequestException("Unsupported container platform '${platform}'")
    }

    /**
     * Given a label formatted as key=value, return it as a map
     *
     * @param label A label composed by a key and a value, separated by a `=` character.
     * @return The same label as Java {@link Map} object
     */
    private Map<String,String> toLabelMap(String label) {
        final parts = label.tokenize('=')
        if( parts.size() != 2 )
            throw new IllegalArgumentException("Label should be specified as 'key=value' -- offending value: '$label'")
        return Map.of(parts[0], parts[1])
    }
}
