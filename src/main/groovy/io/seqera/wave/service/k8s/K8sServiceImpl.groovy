package io.seqera.wave.service.k8s

import java.nio.file.Path
import java.time.Duration
import javax.annotation.Nullable
import javax.annotation.PostConstruct

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1ContainerStateTerminated
import io.kubernetes.client.openapi.models.V1DeleteOptions
import io.kubernetes.client.openapi.models.V1HostPathVolumeSource
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobBuilder
import io.kubernetes.client.openapi.models.V1PersistentVolumeClaimVolumeSource
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodBuilder
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import io.kubernetes.client.openapi.models.V1Volume
import io.kubernetes.client.openapi.models.V1VolumeMount
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.configuration.SpackConfig
import io.seqera.wave.core.ContainerPlatform
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * implements the support for Kubernetes cluster
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Requires(property = 'wave.build.k8s')
@CompileStatic
class K8sServiceImpl implements K8sService {

    @Value('${wave.build.k8s.namespace}')
    private String namespace

    @Value('${wave.build.k8s.debug:false}')
    private boolean debug

    @Value('${wave.build.k8s.storage.claimName}')
    @Nullable
    private String storageClaimName

    @Value('${wave.build.k8s.storage.mountPath}')
    @Nullable
    private String storageMountPath

    @Value('${wave.build.workspace}')
    private String buildWorkspace

    @Value('${wave.build.timeout:5m}')
    private Duration buildTimeout

    @Property(name='wave.build.k8s.labels')
    @Nullable
    private Map<String, String> labels

    @Property(name='wave.build.k8s.node-selector')
    @Nullable
    private Map<String, String> nodeSelectorMap

    @Value('${wave.build.k8s.service-account}')
    @Nullable
    private String serviceAccount

    @Value('${wave.build.k8s.resources.requests.cpu}')
    @Nullable
    private String requestsCpu

    @Value('${wave.build.k8s.resources.requests.memory}')
    @Nullable
    private String requestsMemory

    @Inject
    private SpackConfig spackConfig

    @Inject
    private K8sClient k8sClient

    /**
     * Validate config setting
     */
    @PostConstruct
    private void init() {
        log.info "K8s build config: namespace=$namespace; service-account=$serviceAccount; node-selector=$nodeSelectorMap; buildTimeout=$buildTimeout; cpus=$requestsCpu; memory=$requestsMemory; buildWorkspace=$buildWorkspace; storageClaimName=$storageClaimName; storageMountPath=$storageMountPath; "
        if( storageClaimName && !storageMountPath )
            throw new IllegalArgumentException("Missing 'wave.build.k8s.storage.mountPath' configuration attribute")
        if( storageMountPath ) {
            if( !buildWorkspace )
                throw new IllegalArgumentException("Missing 'wave.build.workspace' configuration attribute")
            if( !Path.of(buildWorkspace).startsWith(storageMountPath) )
                throw new IllegalArgumentException("Build workspace should be a sub-directory of 'wave.build.k8s.storage.mountPath' - offending value: '$buildWorkspace' - expected value: '$storageMountPath'")
        }
        // validate node selectors
        final platforms = nodeSelectorMap ?: Collections.<String,String>emptyMap()
        for( Map.Entry<String,String> it : platforms ) {
            log.debug "Checking container platform '$it.key'; selector '$it.value'"
            ContainerPlatform.of(it.key) // <-- if invalid it will throw an exception
        }
    }

    /**
     * Create a K8s job with the specified name
     *
     * @param name
     *      The K8s job name. It must be unique
     * @param containerImage
     *      The container image to be used to run the job
     * @param args
     *      The command to be executed by the job
     * @return
     *      An instance of {@link V1Job}
     */
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
                .createNamespacedJob(namespace, body, null, null, null,null)
    }

    /**
     * Get a Jobs Job.
     *
     * @param name The job name
     * @return An instance of {@link V1Job}
     */
    @Override
    V1Job getJob(String name) {
        k8sClient
                .batchV1Api()
                .readNamespacedJob(name, namespace, null)
    }

    /**
     * Get a Job status
     *
     * @param name The job name
     * @return  An instance of {@link JobStatus}
     */
    @Override
    JobStatus getJobStatus(String name) {
        def job = k8sClient
                .batchV1Api()
                .readNamespacedJob(name, namespace, null)
        if( !job )
            return null
        if( job.status.succeeded==1 )
            return JobStatus.Succeeded
        if( job.status.failed>0 )
            return JobStatus.Failed
        return JobStatus.Pending
    }

    /**
     * Get pod description
     *
     * @param name The pod name
     * @return An instance of {@link V1Pod} representing the job description
     */
    @Override
    V1Pod getPod(String name) {
        return k8sClient
                .coreV1Api()
                .readNamespacedPod(name, namespace, null)
    }

    /**
     * Create a volume mount for the build storage.
     *
     * @param workDir The path representing a container build context
     * @param storageMountPath
     * @return A {@link V1VolumeMount} representing the mount path for the build config
     */
    protected V1VolumeMount mountBuildStorage(Path workDir, String storageMountPath) {
        assert workDir, "K8s mount build storage is mandatory"

        final vol = new V1VolumeMount()
                .name('build-data')
                .mountPath(workDir.toString())
                .readOnly(true)

        if( storageMountPath ) {
            // check sub-path
            final rel = Path.of(storageMountPath).relativize(workDir).toString()
            if (rel)
                vol.subPath(rel)
        }
        return vol
    }

    /**
     * Defines the volume for the container building shared context
     *
     * @param workDir The path where the container image build context is located
     * @param claimName The claim name of the corresponding storage
     * @return An instance of {@link V1Volume} representing the build storage volume
     */
    protected V1Volume volumeBuildStorage(String mountPath, @Nullable String claimName) {
        final vol= new V1Volume()
                .name('build-data')
        if( claimName ) {
            vol.persistentVolumeClaim( new V1PersistentVolumeClaimVolumeSource().claimName(claimName) )
        }
        else {
            vol.hostPath( new V1HostPathVolumeSource().path(mountPath) )
        }

        return vol
    }

    /**
     * Defines the volume mount for the Kaniko docker config
     *
     * @return A {@link V1VolumeMount} representing the docker config for kaniko
     */
    protected V1VolumeMount mountDockerConfig(Path workDir, String storageMountPath) {
        assert workDir, "K8s mount build storage is mandatory"

        final rel = Path.of(storageMountPath).relativize(workDir).toString()
        final String config = rel ? "$rel/config.json" : 'config.json'
        return new V1VolumeMount()
                .name('build-data')
                .mountPath('/kaniko/.docker/config.json')
                .subPath(config)
                .readOnly(true)
    }

    protected V1VolumeMount mountSpackCacheDir(Path spackCacheDir, String storageMountPath, String containerPath) {
        final rel = Path.of(storageMountPath).relativize(spackCacheDir).toString()
        if( !rel || rel.startsWith('../') )
            throw new IllegalArgumentException("Spack cacheDirectory '$spackCacheDir' must be a sub-directory of storage path '$storageMountPath'")
        return new V1VolumeMount()
                .name('build-data')
                .mountPath(containerPath)
                .subPath(rel)
    }

    protected V1VolumeMount mountSpackSecretFile(Path secretFile, String storageMountPath, String containerPath) {
        final rel = Path.of(storageMountPath).relativize(secretFile).toString()
        if( !rel || rel.startsWith('../') )
            throw new IllegalArgumentException("Spack secretKeyFile '$secretFile' must be a sub-directory of storage path '$storageMountPath'")
        return new V1VolumeMount()
                .name('build-data')
                .readOnly(true)
                .mountPath(containerPath)
                .subPath(rel)
    }

    /**
     * Create a container for container image building via Kaniko
     *
     * @param name
     *      The name of pod
     * @param containerImage
     *      The container image to be used
     * @param args
     *      The build command to be performed
     * @param workDir
     *      The build context directory
     * @param creds
     *      The target container repository credentials
     * @return
     *      The {@link V1Pod} description the submitted pod
     */
    @Override
    @CompileDynamic
    V1Pod buildContainer(String name, String containerImage, List<String> args, Path workDir, Path creds, SpackConfig spackConfig, Map<String,String> nodeSelector) {
        final spec = buildSpec(name, containerImage, args, workDir, creds, spackConfig, nodeSelector)
        return k8sClient
                .coreV1Api()
                .createNamespacedPod(namespace, spec, null, null, null,null)
    }

    V1Pod buildSpec(String name, String containerImage, List<String> args, Path workDir, Path creds, SpackConfig spackConfig, Map<String,String> nodeSelector) {

        // required volumes
        final mounts = new ArrayList<V1VolumeMount>(5)
        mounts.add(mountBuildStorage(workDir, storageMountPath))

        final volumes = new ArrayList<V1Volume>(5)
        volumes.add(volumeBuildStorage(storageMountPath, storageClaimName))

        if( creds ){
            mounts.add(0, mountDockerConfig(workDir, storageMountPath))
        }

        if( spackConfig ) {
            mounts.add(mountSpackCacheDir(spackConfig.cacheDirectory, storageMountPath, spackConfig.cacheMountPath))
            mounts.add(mountSpackSecretFile(spackConfig.secretKeyFile, storageMountPath, spackConfig.secretMountPath))
        }

        V1PodBuilder builder = new V1PodBuilder()

        //metadata section
        builder.withNewMetadata()
                .withNamespace(namespace)
                .withName(name)
                .addToLabels(labels)
                .endMetadata()

        //spec section
        def spec = builder
                .withNewSpec()
                .withNodeSelector(nodeSelector)
                .withServiceAccount(serviceAccount)
                .withActiveDeadlineSeconds( buildTimeout.toSeconds() )
                .withRestartPolicy("Never")
                .addAllToVolumes(volumes)


        final requests = new V1ResourceRequirements()
        if( requestsCpu )
            requests.putRequestsItem('cpu', new Quantity(requestsCpu))
        if( requestsMemory )
            requests.putRequestsItem('memory', new Quantity(requestsMemory))

        //container section
        spec.addNewContainer()
                .withName(name)
                .withImage(containerImage)
                .withArgs(args)
                .withVolumeMounts(mounts)
                .withResources(requests)
            .endContainer()
            .endSpec()

        builder.build()
    }

    /**
     * Wait for a pod a completion
     *
     * @param pod
     *      The pod name
     * @param timeout
     *      Max wait time in milliseconds
     * @return
     *      An instance of {@link V1ContainerStateTerminated} representing the termination state
     *      or {@code null} if the state cannot be determined or timeout was reached,
     */
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

    /**
     * Fetch the logs of a pod
     *
     * @param name The pod name
     * @return The logs as a string or when logs are not available or cannot be accessed
     */
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

    /**
     * Delete a pod
     *
     * @param name The name of the pod to be deleted
     */
    @Override
    void deletePod(String name) {
        k8sClient
                .coreV1Api()
                .deleteNamespacedPod(name, namespace, (String)null, (String)null, (Integer)null, (Boolean)null, (String)null, (V1DeleteOptions)null)
    }
}
