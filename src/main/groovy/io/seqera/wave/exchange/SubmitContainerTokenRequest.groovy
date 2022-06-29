package io.seqera.wave.exchange

import groovy.transform.CompileStatic
import io.seqera.wave.model.ContainerConfig
/**
 * Model a request for an augmented container
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class SubmitContainerTokenRequest {

    /**
     * Tower access token required to enable the service
     */
    String towerAccessToken

    /**
     * Tower workspace id
     */
    Long towerWorkspaceId

    /**
     * Container image to be pulled
     */
    String containerImage

    /**
     * Container build file i.g. Dockerfile of the container to be build
     */
    String containerFile

    /**
     * List of layers to be added in the pulled image
     */
    ContainerConfig containerConfig

}
