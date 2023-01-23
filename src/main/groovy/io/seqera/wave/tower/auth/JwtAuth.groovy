package io.seqera.wave.tower.auth

import groovy.transform.Canonical

/**
 * Models JWT authorization tokens
 * used to connect with Tower service
 */
@Canonical
class JwtAuth {

    /**
     * The bearer authorization token
     */
    String bearer

    /**
     * The refresh token to request an updated authorization token
     */
    String refresh
    
}
