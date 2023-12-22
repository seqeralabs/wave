package io.seqera.wave.service.blob.transfer

import io.seqera.wave.service.blob.BlobInfo
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface TransferStrategy {

    BlobInfo transfer(BlobInfo info, List<String> command)

}
