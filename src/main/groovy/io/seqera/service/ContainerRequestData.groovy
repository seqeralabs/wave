package io.seqera.service

import groovy.transform.CompileStatic
import io.seqera.model.ContainerCoordinates

/**
 * Model a container request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ContainerRequestData {
    Long userId
    Long workspaceId
    String containerImage

    ContainerCoordinates coordinates() { ContainerCoordinates.parse(containerImage) }
}
