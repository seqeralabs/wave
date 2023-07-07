package io.seqera.wave.service.inspect

import io.micronaut.core.annotation.Nullable
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerInspectService {

    String credentialsConfigJson(String containerFile, String buildRepo, String cacheRepo, @Nullable Long userId, @Nullable Long workspaceId, @Nullable String towerToken, @Nullable String towerEndpoint)

    List<String> containerEntrypoint(String containerFile, @Nullable Long userId, @Nullable Long workspaceId, @Nullable String towerToken, @Nullable String towerEndpoint)

}
