package io.seqera.wave.service.k8s

import java.nio.file.Path
import javax.annotation.Nullable

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1DeleteOptions
import io.kubernetes.client.openapi.models.V1EmptyDirVolumeSource
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobBuilder
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodBuilder
import io.micronaut.context.annotation.Value
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * implements the support for Kubernetes cluster
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class K8sServiceImpl implements K8sService {

    @Value('${wave.build.k8s.namespace}')
    private String namespace

    @Value('${wave.build.k8s.debug:false}')
    private boolean debug

    @Value('${wave.build.k8s.volumeClaim}')
    @Nullable
    private String volumeClaim

    @Inject
    private K8sClient k8sClient

    @Override
    @CompileDynamic
    V1Job createJob(String name, String containerImage, List<String> args) {

        V1Job body = new V1JobBuilder()
                .withNewMetadata()
                    .withNamespace(namespace)
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

        return k8sClient
                .batchV1Api()
                .createNamespacedJob(namespace, body, null, null, null)
    }

    @Override
    V1Job getJob(String name) {
        k8sClient
                .batchV1Api()
                .readNamespacedJob(name, namespace, null, null, null)
    }

    @Override
    JobStatus getJobStatus(String name) {
        def job = k8sClient
                .batchV1Api()
                .readNamespacedJob(name, namespace, null, null, null)
        if( !job )
            return null
        if( job.status.succeeded==1 )
            return JobStatus.Succeeded
        if( job.status.failed>0 )
            return JobStatus.Failed
        return JobStatus.Pending
    }

    @Override
    V1Pod getPod(String name) {
        return k8sClient
                .coreV1Api()
                .readNamespacedPod(name, namespace, null, null, null)
    }

    @Override
    @CompileDynamic
    V1Pod buildContainer(String name, String containerImage, List<String> args, Path workDir, String creds) {
        V1Pod spec = new V1PodBuilder()
                .withNewMetadata()
                    .withNamespace(namespace)
                    .withName(name)
                .endMetadata()
                .withNewSpec()
                    .addNewInitContainer()
                        .withName('init-secret')
                        .withImage('busybox')
                        .withCommand(['sh','-c',"echo '$creds' > /kaniko/.docker/config.json".toString()])
                        .addNewVolumeMount()
                            .withName('docker-config')
                            .withMountPath('/kaniko/.docker/')
                        .endVolumeMount()
                    .endInitContainer()
                    .addNewContainer()
                        .withName(name)
                        .withImage(containerImage)
                        .withArgs(args)
                        .addNewVolumeMount()
                            .withName('docker-config')
                            .withMountPath('/kaniko/.docker/')
                        .endVolumeMount()
                        .addNewVolumeMount()
                            .withName('build-data')
                            .withMountPath(workDir.toString())
                        .endVolumeMount()
                    .endContainer()
                    .withRestartPolicy("Never")
                    .addNewVolume()
                        .withName('docker-config')
                        .withEmptyDir( new V1EmptyDirVolumeSource() )
                    .endVolume()
                    .addNewVolume()
                        .withName('build-data')
                        .withHostPath( new V1HostPathVolumeSource().path(workDir.toString()) )
                    .endVolume()
                .endSpec()
                .build()

        return k8sClient
                .coreV1Api()
                .createNamespacedPod(namespace, spec, null, null, null)
    }

    @Override
    V1ContainerStateTerminated waitPod(V1Pod pod, long timeout) {
        final name = pod.metadata.name
        final start = System.currentTimeMillis()
        // wait for termination
        while( true ) {
            final phase = pod.status?.phase
            if(  phase && phase != 'Pending' ) {
                final status = pod.status.containerStatuses.find( it -> it.name==name )
                if( !status )
                    return null
                if( !status.state )
                    return null
                if( status.state.terminated ) {
                    return status.state.terminated
                }
            }

            if( phase == 'Failed' )
                return null
            final delta = System.currentTimeMillis()-start
            if( delta > timeout )
                return null
            sleep 5_000
            pod = getPod(name)
        }
    }

    @Override
    String logsPod(String name) {
        try {
            final logs = k8sClient.podLogs()
            logs.streamNamespacedPodLog(namespace, name, name).getText()
        }
        catch (Exception e) {
            log.error "Unable to fetch logs for pod: $name", e
            return null
        }
    }

    @Override
    void deletePod(String name) {
        k8sClient
                .coreV1Api()
                .deleteNamespacedPod(name, namespace, (String)null, (String)null, (Integer)null, (Boolean)null, (String)null, (V1DeleteOptions)null)
    }
}
