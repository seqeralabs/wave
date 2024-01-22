package io.seqera.wave.service.blob.signing

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.blob.BlobSigningService
import jakarta.inject.Singleton

/**
 * Implements no url signing
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingProperty = 'wave.blobCache.signing-strategy')
@Singleton
@CompileStatic
class NoBlobSigningService implements BlobSigningService{

    @Override
    String createSignedUri(String uri) {
        return uri
    }
}
