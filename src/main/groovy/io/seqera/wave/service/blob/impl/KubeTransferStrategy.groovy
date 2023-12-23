package io.seqera.wave.service.blob.impl

import com.google.common.hash.Hashing
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.BlobCacheInfo
import io.seqera.wave.service.blob.TransferStrategy
import io.seqera.wave.service.k8s.K8sService
import jakarta.inject.Inject
/**
 * Implements {@link TransferStrategy} that runs s5cmd using a
 * Kubernetes job
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Requires(property = 'wave.build.k8s')
@Replaces(SimpleTransferStrategy)
class KubeTransferStrategy implements TransferStrategy {

    @Inject
    private BlobCacheConfig blobConfig

    @Inject
    private K8sService k8sService

    @Override
    BlobCacheInfo transfer(BlobCacheInfo info, List<String> command) {
        final podName = podName(info)
        final pod = k8sService.transferContainer(podName, blobConfig.s5Image, command, blobConfig)
        final terminated = k8sService.waitPod(pod, blobConfig.transferTimeout.toMillis())
        final stdout = k8sService.logsPod(podName)
        return terminated
                ? info.completed(terminated.exitCode, stdout)
                : info.failed(stdout)
    }

    protected String podName(BlobCacheInfo info) {
        return 'transfer-' + Hashing
                .sipHash24()
                .newHasher()
                .putUnencodedChars(info.locationUri)
                .putUnencodedChars(info.creationTime.toString())
                .hash()
    }
}
