package io.seqera.wave.service.blob.signing

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.blob.BlobSigningService
import jakarta.inject.Singleton

/**
 * Implements a signing strategy based on Cloudflare WAF token
 * https://developers.cloudflare.com/waf/custom-rules/use-cases/configure-token-authentication/#option-2-configure-using-waf-custom-rules
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(property = 'wave.blobCache.signing-strategy', value = 'cloudflare-waf-token')
@Singleton
@CompileStatic
class CloudflareBlobSigningService implements BlobSigningService {
    @Override
    String createSignedUri(String uri) {
        return null
    }
}
