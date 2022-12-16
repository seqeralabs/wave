package io.seqera.wave.auth

import io.micronaut.core.annotation.Nullable
import io.seqera.wave.core.ContainerPath
/**
 * Model an abstract provider for container registry credentials
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface RegistryCredentialsProvider {

    /**
     * Provides the credentials for the specified registry.
     *
     * @param registry
     *      A registry name e.g. docker.io or quay.io. When empty {@code docker.io} is assumed.
     * @return
     *      A {@link RegistryCredentials} object holding the credentials for the specified registry or {@code null}
     *      if not credentials can be found
     */
    RegistryCredentials getDefaultCredentials(String registry)

    /**
     * Provides the credentials for the specified container associated with the user and tower
     * workspace specified.
     *
     * @param container
     *      A container name e.g. docker.io/library/ubuntu.
     * @param userId
     *      The tower user Id.
     * @param workspaceId
     *      The tower workspace Id.
     * @param towerToken
     *      The token used to authenticate with tower
     * @param towerEndpoint
     *      The tower endpoint used in the registration
     * @return
     *      A {@link RegistryCredentials} object holding the credentials for the specified container or {@code null}
     *      if not credentials can be found
     */
    RegistryCredentials getUserCredentials(ContainerPath container, @Nullable Long userId, @Nullable Long workspaceId,String towerToken,String towerEndpoint)


}
