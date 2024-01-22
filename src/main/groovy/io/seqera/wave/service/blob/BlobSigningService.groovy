package io.seqera.wave.service.blob

/**
 * Define the contract for creating signing URLs
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BlobSigningService {

    String createSignedUri(String uri)

}
