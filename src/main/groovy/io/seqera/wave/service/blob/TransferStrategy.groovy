package io.seqera.wave.service.blob
/**
 * Defines the contract to transfer a layer blob into a remote object storage
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface TransferStrategy {

    BlobCacheInfo transfer(BlobCacheInfo info, List<String> command)

}
