package io.seqera.wave.service.k8s

import java.nio.file.Path

import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1Pod
import io.seqera.wave.configuration.SpackConfig

/**
 * Defines Kubernetes operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface K8sService {

    enum JobStatus { Pending, Running, Succeeded, Failed }

    V1Job createJob(String name, String containerImage, List<String> args)

    V1Job createJobWithCredentials(String name, String containerImage, List<String> args, String mountConfigFile, String credsFile)

    V1Job getJob(String name)

    JobStatus getJobStatus(String name)

    V1Pod getPod(String name)

    String logsPod(String name)

    void deletePod(String name)

    V1Pod buildContainer(String name, String containerImage, List<String> args, Path workDir, Path creds, SpackConfig spackConfig, Map<String,String> nodeSelector)

    V1ContainerStateTerminated waitPod(V1Pod pod, long timeout)
}
