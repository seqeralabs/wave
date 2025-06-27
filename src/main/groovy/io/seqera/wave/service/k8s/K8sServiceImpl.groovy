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

package io.seqera.wave.service.k8s

import java.time.Duration
import java.util.regex.Pattern
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.kubernetes.client.custom.Quantity
import io.kubernetes.client.openapi.models.V1Capabilities
import io.kubernetes.client.openapi.models.V1ContainerBuilder
import io.kubernetes.client.openapi.models.V1EnvVar
import io.kubernetes.client.openapi.models.V1Job
import io.kubernetes.client.openapi.models.V1JobBuilder
import io.kubernetes.client.openapi.models.V1JobCondition
import io.kubernetes.client.openapi.models.V1JobStatus
import io.kubernetes.client.openapi.models.V1Pod
import io.kubernetes.client.openapi.models.V1PodDNSConfig
import io.kubernetes.client.openapi.models.V1PodDNSConfigBuilder
import io.kubernetes.client.openapi.models.V1PodFailurePolicy
import io.kubernetes.client.openapi.models.V1PodFailurePolicyOnExitCodesRequirement
import io.kubernetes.client.openapi.models.V1PodFailurePolicyOnPodConditionsPattern
import io.kubernetes.client.openapi.models.V1PodFailurePolicyRule
import io.kubernetes.client.openapi.models.V1ResourceRequirements
import io.kubernetes.client.openapi.models.V1SecurityContext
import io.micronaut.context.annotation.Property
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.util.trace.TraceElapsedTime
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.configuration.MirrorConfig
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.util.FusionHelper
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

    @Value('${wave.build.k8s.dns.servers}')
    @Nullable
    private List<String> dnsServers

    @Value('${wave.build.k8s.dns.policy}')
    @Nullable
    private String dnsPolicy

    @Value('${wave.build.k8s.namespace}')
    private String namespace

    @Value('${wave.build.k8s.debug:false}')
    private boolean debug

    @Value('${wave.build.k8s.storage.claimName}')
    @Nullable
    private String storageClaimName

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

    @Value('${wave.build.k8s.resources.limits.cpu}')
    @Nullable
    private String limitsCpu

    @Value('${wave.build.k8s.resources.limits.memory}')
    @Nullable
    private String limitsMemory

    @Inject
    private K8sClient k8sClient

    @Inject
    private BuildConfig buildConfig

    private Map<String, String> getBuildkitAnnotations(String containerName, boolean singularity) {
        if( singularity )
            return null
        final key = "container.apparmor.security.beta.kubernetes.io/${containerName}".toString()
        return Map.of(key, "unconfined")
    }

    /**
     * Validate config setting
     */
    @PostConstruct
    private void init() {
        log.info "K8s build config: namespace=$namespace; service-account=$serviceAccount; node-selector=$nodeSelectorMap; cpus=$requestsCpu; memory=$requestsMemory; buildWorkspaceBucket=$buildConfig.buildWorkspace;"
        if( !buildConfig.buildWorkspace )
            throw new IllegalArgumentException("Missing 'wave.build.workspaceBucket' configuration attribute")
        // validate node selectors
        final platforms = nodeSelectorMap ?: Collections.<String,String>emptyMap()
        for( Map.Entry<String,String> it : platforms ) {
            log.debug "Checking container platform '$it.key'; selector '$it.value'"
            ContainerPlatform.of(it.key) // <-- if invalid it will throw an exception
        }
    }

    /**
     * Get a Job status
     *
     * @param name The job name
     * @return  An instance of {@link JobStatus}
     */
    @Override
    @TraceElapsedTime(thresholdMillis = '${wave.trace.k8s.threshold:200}')
    JobStatus getJobStatus(String name) {
        final job = k8sClient
                .batchV1Api()
                .readNamespacedJob(name, namespace)
                .execute()
        if( !job ) {
            log.warn "K8s job=$name - unknown"
            return null
        }

        final result = jobStatus0(job.status, job.spec?.backoffLimit)
        log.trace "K8s job=$name - result=$result; backoff-limit=${job.spec?.backoffLimit}; status=${job.status}"
        return result
    }

    private JobStatus jobStatus0(V1JobStatus status, Integer backoffLimit) {
        if( status.succeeded )
            return JobStatus.Succeeded
        if( status.active )
            return JobStatus.Pending
        if( status.failed ) {
            if( status.completionTime!=null )
                return JobStatus.Failed
            if( backoffLimit!=null && status.failed > backoffLimit )
                return JobStatus.Failed
            if( status.conditions?.any( c-> isPodFailCondition(c)) )
                return JobStatus.Failed
        }
        return JobStatus.Pending
    }

    /**
     * Determines if the job has failed due the rules defined by {@link #failurePolicy()}
     *
     * @param condition The job condition to be evaluated
     * @return {@code true} if the job condition identifies a failure, {@code false otherwise}
     */
    static final private Pattern FAIL_MESSAGE = ~/Container .+ failed .+ matching FailJob rule at index 2/

    static protected boolean isPodFailCondition(V1JobCondition condition) {
        return condition.reason=='PodFailurePolicy'
                && condition.message
                && FAIL_MESSAGE.matcher(condition.message).matches()
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
                .readNamespacedPod(name, namespace)
                .execute()
    }

    /**
     * Fetch the logs of a pod.
     *
     * NOTE: this method assume the pod runs exactly *one* container.
     *
     * @param name The {@link V1Pod} object representing the pod from where retrieve the logs
     * @return The logs as a string or when logs are not available or cannot be accessed
     */
    @Override
    @TraceElapsedTime(thresholdMillis = '${wave.trace.k8s.threshold:200}')
    String logsPod(V1Pod pod) {
        try {
            final logs = k8sClient.podLogs()
            logs.streamNamespacedPodLog(namespace, pod.metadata.name, pod.spec.containers.first().name).getText()
        }
        catch (Exception e) {
            log.error "Unable to fetch logs for pod: ${pod.metadata.name} - cause: ${e.message}"
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
                .deleteNamespacedPod(name, namespace)
                .execute()
    }


    private void addAWSCreds(Map <String, String> env) {
        env.put('AWS_ACCESS_KEY_ID', "${System.getenv('AWS_ACCESS_KEY_ID')}".toString())
        env.put('AWS_SECRET_ACCESS_KEY', "${System.getenv('AWS_SECRET_ACCESS_KEY')}".toString())
    }

    /**
     * Create a Job for blob transfer
     *
     * @param name
     *      The name of job and container
     * @param containerImage
     *      The container image to be used
     * @param args
     *      The transfer command to be performed
     * @param blobConfig
     *      The config to be used for transfer
     * @return
     *      The {@link V1Job} description the submitted job
     */
    @Override
    V1Job launchTransferJob(String name, String containerImage, List<String> args, BlobCacheConfig blobConfig) {
        final spec = createTransferJobSpec(name, containerImage, args, blobConfig)

        return k8sClient
                .batchV1Api()
                .createNamespacedJob(namespace, spec)
                .execute()
    }

    protected V1PodDNSConfig dnsConfig() {
        return dnsServers
                ? new V1PodDNSConfigBuilder().withNameservers(dnsServers).build()
                : null
    }

    V1Job createTransferJobSpec(String name, String containerImage, List<String> args, BlobCacheConfig blobConfig) {

        V1JobBuilder builder = new V1JobBuilder()

        //metadata section
        builder.withNewMetadata()
                .withNamespace(namespace)
                .withName(name)
                .withLabels(labels)
                .endMetadata()

        final requests = new V1ResourceRequirements()
        if( blobConfig.requestsCpu )
            requests.putRequestsItem('cpu', new Quantity(blobConfig.requestsCpu))
        if( blobConfig.requestsMemory )
            requests.putRequestsItem('memory', new Quantity(blobConfig.requestsMemory))
        if( blobConfig.limitsCpu )
            requests.putLimitsItem('cpu', new Quantity(blobConfig.limitsCpu))
        if( blobConfig.limitsMemory )
            requests.putLimitsItem('memory', new Quantity(blobConfig.limitsMemory))

        //spec section
        def spec = builder.withNewSpec()
                .withBackoffLimit(blobConfig.retryAttempts)
                .withNewTemplate()
                    .editOrNewSpec()
                    .withServiceAccount(serviceAccount)
                    .withRestartPolicy("Never")
                    .withDnsConfig(dnsConfig())
                    .withDnsPolicy(dnsPolicy)
        //container section
                    .addNewContainer()
                        .withName(name)
                        .withImage(containerImage)
                        .withArgs(args)
                        .withResources(requests)
                        .withEnv(toEnvList(blobConfig.getEnvironment()))
                    .endContainer()
                .endSpec()
                .endTemplate()
                .endSpec()

        return spec.build()
    }

    /**
     * Create a container for container image building via buildkit
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
    @TraceElapsedTime(thresholdMillis = '${wave.trace.k8s.threshold:200}')
    V1Job launchBuildJob(String name, String containerImage, List<String> args, String workspace, String creds, Duration timeout, Map<String,String> nodeSelector) {
        final spec = buildJobSpec(name, containerImage, args, workspace, creds, timeout, nodeSelector)
        return k8sClient
                .batchV1Api()
                .createNamespacedJob(namespace, spec)
                .execute()
    }

    V1Job buildJobSpec(String name, String containerImage, List<String> args, String workDir, String credsFile, Duration timeout, Map<String,String> nodeSelector) {

        // dirty dependency to avoid introducing another parameter
        final singularity = containerImage.contains('singularity')

        Map<String, String> env = new HashMap<String, String>()
        addAWSCreds(env)

        if( credsFile ){
                env.put('DOCKER_CONFIG', "${FusionHelper.getFusionPath(buildConfig.workspaceBucketName, workDir)}".toString())
        }

        V1JobBuilder builder = new V1JobBuilder()

        //metadata section
        builder.withNewMetadata()
                .withNamespace(namespace)
                .withName(name)
                .addToLabels(labels)
                .endMetadata()

        //spec section
        final spec = builder
                .withNewSpec()
                .withBackoffLimit(buildConfig.retryAttempts)
                .withPodFailurePolicy(failurePolicy())
                .withNewTemplate()
                .withNewMetadata()
                .addToAnnotations(getBuildkitAnnotations(name,singularity))
                .endMetadata()
                .editOrNewSpec()
                .withDnsConfig(dnsConfig())
                .withDnsPolicy(dnsPolicy)
                .withNodeSelector(nodeSelector)
                .withServiceAccount(serviceAccount)
                .withActiveDeadlineSeconds( timeout.toSeconds() )
                .withRestartPolicy("Never")

        final requests = new V1ResourceRequirements()
        if( requestsCpu )
            requests.putRequestsItem('cpu', new Quantity(requestsCpu))
        if( requestsMemory )
            requests.putRequestsItem('memory', new Quantity(requestsMemory))

        if( limitsCpu )
            requests.putLimitsItem('cpu', new Quantity(limitsCpu))
        if( limitsMemory )
            requests.putLimitsItem('memory', new Quantity(limitsMemory))

        //add https://github.com/nextflow-io/k8s-fuse-plugin
        requests.limits(Map.of("nextflow.io/fuse", new Quantity("1")))

        env.put('TMPDIR', '/tmp')

        // container section
        final container = new V1ContainerBuilder()
                .withName(name)
                .withImage(containerImage)
                .withResources(requests)
                .withEnv(toEnvList(env))
                .withArgs(args)
                .withNewSecurityContext().withPrivileged(true).endSecurityContext()

        // spec section
        spec.withContainers(container.build()).endSpec().endTemplate().endSpec()

        return builder.build()
    }

    @Override
    V1Job launchScanJob(String name, String containerImage, List<String> args, String workspace, String creds, ScanConfig scanConfig) {
        final spec = scanJobSpec(name, containerImage, args, workspace, creds, scanConfig)
        return k8sClient
                .batchV1Api()
                .createNamespacedJob(namespace, spec)
                .execute()
    }

    V1Job scanJobSpec(String name, String containerImage, List<String> args, String workDir, String creds, ScanConfig scanConfig) {

        Map<String, String> env = new HashMap<String, String>()
        addAWSCreds(env)
        if( creds ){
            env.put('DOCKER_CONFIG', workDir)
        }

        V1JobBuilder builder = new V1JobBuilder()

        //metadata section
        builder.withNewMetadata()
                .withNamespace(namespace)
                .withName(name)
                .addToLabels(labels)
                .endMetadata()

        //spec section
        def spec = builder
                .withNewSpec()
                .withBackoffLimit(buildConfig.retryAttempts)
                .withPodFailurePolicy(failurePolicy())
                .withNewTemplate()
                .editOrNewSpec()
                .withServiceAccount(serviceAccount)
                .withRestartPolicy("Never")
                .withDnsConfig(dnsConfig())
                .withDnsPolicy(dnsPolicy)

        final requests = new V1ResourceRequirements()
        if( scanConfig.requestsCpu )
            requests.putRequestsItem('cpu', new Quantity(scanConfig.requestsCpu))
        if( scanConfig.requestsMemory )
            requests.putRequestsItem('memory', new Quantity(scanConfig.requestsMemory))
        if( scanConfig.limitsCpu )
            requests.putLimitsItem('cpu', new Quantity(scanConfig.limitsCpu))
        if( scanConfig.limitsMemory )
            requests.putLimitsItem('memory', new Quantity(scanConfig.limitsMemory))

        //add https://github.com/nextflow-io/k8s-fuse-plugin
        requests.limits(Map.of("nextflow.io/fuse", new Quantity("1")))

        // container section
        final container = new V1ContainerBuilder()
                .withName(name)
                .withImage(containerImage)
                .withArgs(args)
                .withResources(requests)
                .withEnv(toEnvList(env))

        final env2 = scanConfig.environmentAsTuples
        for( Tuple2 entry : env2 ) {
            final String k = entry.v1
            final String v = entry.v2
            container.addToEnv(new V1EnvVar().name(k).value(v))
        }

        container.withNewSecurityContext().withPrivileged(true).endSecurityContext()
        // spec section
        spec.withContainers(container.build()).endSpec().endTemplate().endSpec()

        return builder.build()
    }

    @Override
    V1Job launchMirrorJob(String name, String containerImage, List<String> args, String workDir, String creds, MirrorConfig config) {
        final spec = mirrorJobSpec(name, containerImage, args, workDir, creds, config)
        return k8sClient
                .batchV1Api()
                .createNamespacedJob(namespace, spec)
                .execute()
    }

    V1Job mirrorJobSpec(String name, String containerImage, List<String> args, String workDir, String credsFile, MirrorConfig config) {

        Map<String, String> env = new HashMap<String, String>()
        addAWSCreds(env)
        if( credsFile ){
            env.put('DOCKER_CONFIG', FusionHelper.getFusionPath(buildConfig.workspaceBucketName, workDir))
        }

        V1JobBuilder builder = new V1JobBuilder()

        //metadata section
        builder.withNewMetadata()
                .withNamespace(namespace)
                .withName(name)
                .addToLabels(labels)
                .endMetadata()

        //spec section
        def spec = builder
                .withNewSpec()
                .withBackoffLimit(buildConfig.retryAttempts)
                .withPodFailurePolicy(failurePolicy())
                .withNewTemplate()
                .editOrNewSpec()
                .withServiceAccount(serviceAccount)
                .withRestartPolicy("Never")
                .withDnsConfig(dnsConfig())
                .withDnsPolicy(dnsPolicy)

        final requests = new V1ResourceRequirements()
        if( config.requestsCpu )
            requests.putRequestsItem('cpu', new Quantity(config.requestsCpu))
        if( config.requestsMemory )
            requests.putRequestsItem('memory', new Quantity(config.requestsMemory))
        if( config.limitsCpu )
            requests.putLimitsItem('cpu', new Quantity(config.limitsCpu))
        if( config.limitsMemory )
            requests.putLimitsItem('memory', new Quantity(config.limitsMemory))

        //add https://github.com/nextflow-io/k8s-fuse-plugin
        requests.limits(Map.of("nextflow.io/fuse", new Quantity("1")))

        // container section
        final container = new V1ContainerBuilder()
                .withName(name)
                .withImage(containerImage)
                .withArgs(args)
                .withEnv(toEnvList(env))
                .withResources(requests)
                .withEnv(new V1EnvVar().name("REGISTRY_AUTH_FILE").value("/tmp/config.json"))

        // spec section
        spec.withContainers(container.build()).endSpec().endTemplate().endSpec()

        return builder.build()
    }

    protected List<V1EnvVar> toEnvList(Map<String,String> env) {
        final result = new ArrayList<V1EnvVar>(env.size())
        for( Map.Entry<String,String> it : env )
            result.add( new V1EnvVar().name(it.key).value(it.value) )
        return result
    }

    /**
     * Delete a job
     *
     * @param name, name of the job to be deleted
     */
    @Override
    void deleteJob(String name) {
        k8sClient
                .batchV1Api()
                .deleteNamespacedJob(name, namespace)
                .propagationPolicy("Foreground")
                .execute()
    }

    @Override
    @TraceElapsedTime(thresholdMillis = '${wave.trace.k8s.threshold:200}')
    V1Pod getLatestPodForJob(String jobName) {
        // list all pods for the given job
        final allPods = k8sClient
                .coreV1Api()
                .listNamespacedPod(namespace)
                .labelSelector("job-name=${jobName}")
                .execute()

        if( !allPods || !allPods.items )
            return null

        // Find the latest created pod among the pods associated with the job
        def latest = allPods.items.get(0)
        for (def pod : allPods.items) {
            if (pod.metadata?.creationTimestamp?.isAfter(latest.metadata.creationTimestamp)) {
                latest = pod
            }
        }
        return latest
    }

    protected V1PodFailurePolicy failurePolicy() {
        // retry policy
        // read more here
        // https://kubernetes.io/blog/2024/08/19/kubernetes-1-31-pod-failure-policy-for-jobs-goes-ga/
        //
        return new V1PodFailurePolicy()
                // not count the failure towards the backoffLimit
                // for pod failure due to "DisruptionTarget" reason
                // this cause the pod to be retried. NOTE this requires "backoffLimit" > 0
                .addRulesItem(new V1PodFailurePolicyRule()
                        .action("Ignore")
                        .addOnPodConditionsItem( new V1PodFailurePolicyOnPodConditionsPattern()
                                .type("DisruptionTarget")) )
                // fail to job for any configuration issue
                .addRulesItem(new V1PodFailurePolicyRule()
                        .action("FailJob")
                        .addOnPodConditionsItem( new V1PodFailurePolicyOnPodConditionsPattern()
                                .type("ConfigIssue")) )
                // fail the job for any non-zero exit status
                .addRulesItem(new V1PodFailurePolicyRule()
                        .action("FailJob")
                        .onExitCodes(new V1PodFailurePolicyOnExitCodesRequirement()
                                .operator("NotIn").values([0]) ))
    }

}
