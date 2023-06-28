package io.seqera.wave.service.scan

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.ApiClient
import io.kubernetes.client.openapi.Configuration
import io.kubernetes.client.openapi.apis.BatchV1Api
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobBuilder
import io.kubernetes.client.util.Config
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Singleton

/**
 * Implements ContainerScanService
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Primary
@Requires(property = 'wave.build.k8s')
@Singleton
@CompileStatic
class KubernetesContainerScanStrategy extends ContainerScanStrategy{
    private  final K8sService k8sService

    KubernetesContainerScanStrategy(K8sService k8sService) {
        this.k8sService = k8sService
    }

    @Override
    String scanContainer(String containerScanner, String containerName) {
        V1Job job = k8sService.createJob("${containerName}-scan",containerScanner,List.of("image","--format", "json", containerName))
        log.info("Container scan job created: ${job.getMetadata().getName()}")
        String jobName = job.getMetadata().getName();
        while(k8sService.getJobStatus(jobName) == K8sService.JobStatus.Succeeded ||k8sService.getJobStatus(jobName) == K8sService.JobStatus.Failed)
        return
    }
}
