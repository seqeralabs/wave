package io.seqera.wave.api


import groovy.transform.CompileStatic
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
     * Tower instance id required to integrate with wave
     */
    String towerInstanceId

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

    /**
     * Conda recipe file used to build the container
     */
    String condaFile

    /**
     * The container platform to be used
     */
    String containerPlatform

    /**
     * The repository where the build container should be pushed
     */
    String buildRepository

    /**
     * The repository where the build container should be pushed
     */
    String cacheRepository

    /**
     * Request timestamp 
     */
    String timestamp

    /**
     * Request unique fingerprint
     */
    String fingerprint

    /**
     * Force a build even when a cached image already exists
     */
    boolean forceBuild
}
