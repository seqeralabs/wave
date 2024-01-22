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
        final secretKeySpec = new SecretKeySpec(secretKey.getBytes(), "HmacSHA256");

        // Initialize the MAC with the SecretKeySpec
        hmacSha256.init(secretKeySpec);

        // Compute the HMAC-SHA-256 hash
        byte[] hashBytes = hmacSha256.doFinal(message.getBytes());

        // Encode the result in Base64
        return Base64.getEncoder().encodeToString(hashBytes);
    }

}
