package io.seqera.wave.service

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.model.ContainerCoordinates
import static io.seqera.wave.util.StringUtils.trunc
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
    final String containerFile
    final ContainerConfig containerConfig
    final String condaFile
    final ContainerPlatform platform
    final String towerToken
    final String towerEndpoint
    final String buildId
    final Boolean buildNew

    ContainerCoordinates coordinates() { ContainerCoordinates.parse(containerImage) }

    @Override
    String toString() {
        return "ContainerRequestData[userId=$userId; workspaceId=$workspaceId; containerImage=$containerImage; containerFile=${trunc(containerFile)}; condaFile=${trunc(condaFile)}; containerConfig=${containerConfig}]"
    }

}
