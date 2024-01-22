package io.seqera.wave.service.blob

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BlobSigningService {

    String createSignedUri(String uri)

}
