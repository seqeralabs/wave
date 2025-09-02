/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
