package io.seqera.wave.exchange

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Introspected
class InspectRequest {

    /**
     * Container image to be inspect
     */
    String containerImage;

    /**
     * Tower access token required to enable the service
     */
    String towerAccessToken;

    /**
     * Tower endpoint: the public address
     * of the tower instance to integrate with wave
     */
    String towerEndpoint;

    /**
     * Tower workspace id
     */
    Long towerWorkspaceId;

}
