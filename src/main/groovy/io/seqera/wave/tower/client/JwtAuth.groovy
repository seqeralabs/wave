package io.seqera.wave.tower.client

import groovy.transform.Canonical

/**
 * Models JWT authorization tokens
 * used to connect with Tower service
 */
@Canonical
class JwtAuth {

    String bearer
    String refresh
    
}
