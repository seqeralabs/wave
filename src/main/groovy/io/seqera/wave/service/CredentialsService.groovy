package io.seqera.wave.service

import io.seqera.wave.model.TowerTokens

/**
 * Declare operations to access container registry credentials from Tower
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CredentialsService {

    ContainerRegistryKeys findRegistryCreds(String registryName, Long userId, Long workspaceId, String towerToken, String towerEndpoint)

}
