package io.seqera.wave.service.blob.transfer

import io.seqera.wave.service.blob.BlobInfo
/**
 * Defines the contract to transfer a layer blob into a remote object storage
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface TransferStrategy {

    BlobInfo transfer(BlobInfo info, List<String> command)

}
