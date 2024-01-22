package io.seqera.wave.service.blob.signing

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.service.blob.BlobSigningService
import io.seqera.wave.util.CryptoHelper
import io.seqera.wave.util.StringUtils
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton

/**
 * Implements a signing strategy based on Cloudflare WAF token
 * https://developers.cloudflare.com/waf/custom-rules/use-cases/configure-token-authentication/#option-2-configure-using-waf-custom-rules
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(property = 'wave.blobCache.signing-strategy', value = 'cloudflare-waf-token')
@Singleton
@CompileStatic
class CloudflareBlobSigningService implements BlobSigningService {

    @Value('${wave.blobCache.cloudflare.secret-key}')
    private String secretKey

    @Value('${wave.blobCache.cloudflare.lifetime}')
    private Duration lifetime

    @PostConstruct
    private void init() {
        log.debug "Creating Cloudflare signing service - lifetime=${lifetime}; secret-key=${StringUtils.redact(secretKey)}"
    }

    @Override
    String createSignedUri(String uri) {
        final now = Instant.now().getEpochSecond()
        final digest = CryptoHelper.computeCloudflareWafToken(URI.create(uri), now, secretKey)
        return uri + '?verify=' + digest
    }

}
