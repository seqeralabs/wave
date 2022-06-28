package io.seqera.service

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.model.ContainerCoordinates

/**
 * Model a container request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class ContainerRequestData {
    final Long userId
    final Long workspaceId
    final String containerImage

    ContainerCoordinates coordinates() { ContainerCoordinates.parse(containerImage) }
}
