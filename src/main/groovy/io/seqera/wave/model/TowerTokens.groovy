package io.seqera.wave.model

import groovy.transform.Canonical

/**
 * Models authorization tokens
 * used to connect with tower
 */
@Canonical
class TowerTokens {

    String authToken

    String refreshToken
}
