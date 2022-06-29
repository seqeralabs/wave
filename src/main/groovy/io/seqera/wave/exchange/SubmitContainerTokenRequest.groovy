package io.seqera.wave.exchange

/**
 * Model a request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SubmitContainerTokenRequest {
    String containerImage
    String towerAccessToken
    Long towerWorkspaceId
}
