package io.seqera.wave.tower.client

import groovy.transform.CompileStatic

/**
 * Models an encrypted credentials keys response
 *
 * @author Andrea Tortorella <andrea.tortorella@seqera.io>
 */
@CompileStatic
class GetCredentialsKeysResponse {

    /**
     * Secret keys associated with the credentials
     * The keys are encrypted using {@link io.seqera.tower.crypto.AsymmetricCipher}
     */
    String keys
}
