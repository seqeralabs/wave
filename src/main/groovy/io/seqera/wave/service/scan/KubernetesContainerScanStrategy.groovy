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
import io.seqera.wave.configuration.ContainerScanConfig
import io.seqera.wave.model.ScanResult
import io.seqera.wave.service.builder.BuildRequest
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

        ScanResult scanResult = new ScanResult()
        scanResult.buildId = buildRequest.id
        scanResult.startTime = Instant.now()

        String image = buildRequest.targetImage

        Path credsFile;
        try{
            if( buildRequest.configJson ) {
                Path scanDir = Files.createDirectories(Path.of(containerScanConfig.workspace))
                credsFile = scanDir.resolve('config.json').toAbsolutePath()
                Files.write(credsFile, JsonOutput.prettyPrint(buildRequest.configJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
            }}catch (Exception e){
            log.warn("Error getting credentials "+e.getMessage())
        }
        V1Job job;
        try {
            job = createJobWithCredentials("${image}-scan", containerScanner, List.of('image',image), "/root/.docker/config.json", credsFile.toString())

            log.info("Container scan job created: ${job.getMetadata().getName()}")
        }catch (Exception e){
            log.warn("Error in creating scan Job in kubernetes : ${e.getMessage()}")
            e.printStackTrace()
        }
        String jobName = job.getMetadata().getName();

        scanResult.duration = Duration.between(scanResult.startTime,Instant.now())
        scanResult.result = jobName

        return scanResult
    }

    @CompileDynamic
    V1Job createJobWithCredentials(String name, String containerImage, List<String> args, String mountConfigFile, String credsFile) {

        V1Job body = new V1JobBuilder()
                .withNewMetadata()
                .withNamespace('default')
                .withName(name)
                .endMetadata()
                .withNewSpec()
                .withBackoffLimit(0)
                .withNewTemplate()
                .editOrNewSpec()
                .addNewContainer()
                .withName(name)
                .withImage(containerImage)
                .withArgs(args)
                .endContainer()
                .withRestartPolicy("Never")
                .endSpec()
                .endTemplate()
                .endSpec()
                .build();
// Create the default Kubernetes client configuration
        ApiClient client = Config.defaultClient();

        // Set the default client configuration
        Configuration.setDefaultApiClient(client);

        // Create an instance of the BatchV1Api client
        BatchV1Api batchApi = new BatchV1Api(client);
        return batchApi
                .createNamespacedJob('default', body, null, null, null,null)
    }
}
