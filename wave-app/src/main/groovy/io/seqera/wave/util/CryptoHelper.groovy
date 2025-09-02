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

package io.seqera.wave.util

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Implement basic crypto utilities
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class CryptoHelper {

    static String computeHmacSha256(String message, String secretKey) {
        assert message, "Missing 'message' argument"
        assert secretKey, "Missing 'secretKey' argument"

        // Get an instance of the HMAC-SHA-256 algorithm
        final hmacSha256 = Mac.getInstance("HmacSHA256");

        // Create a SecretKeySpec object using the secret key bytes and algorithm
        final secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256")

        // Initialize the MAC with the SecretKeySpec
        hmacSha256.init(secretKeySpec);

        // Compute the HMAC-SHA-256 hash
        byte[] hashBytes = hmacSha256.doFinal(message.getBytes());

        // Encode the result in Base64
        final base64 = Base64.getEncoder().encodeToString(hashBytes);

        // finally URL encode it
        return URLEncoder.encode(base64, 'UTF-8')
    }

    static String computeCloudflareWafToken(URI uri, long now, String secret) {
        final message = uri.path + now.toString()
        final digest = computeHmacSha256(message, secret)
        return "${now}-${digest}"
    }
}
