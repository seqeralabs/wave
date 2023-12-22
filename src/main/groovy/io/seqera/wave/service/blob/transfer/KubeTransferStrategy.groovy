package io.seqera.wave.service.blob.transfer

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BlobConfig
import io.seqera.wave.service.blob.BlobInfo
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Requires(property = 'wave.build.k8s')
@Replaces(LocalTransferStrategy)
class KubeTransferStrategy implements TransferStrategy {

    @Inject
    private BlobConfig blobConfig

    @Override
    BlobInfo transfer(BlobInfo info, List<String> command) {
        return null
    }
}
