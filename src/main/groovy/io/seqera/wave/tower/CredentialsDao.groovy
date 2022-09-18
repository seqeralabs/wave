package io.seqera.wave.tower

import io.micronaut.data.repository.CrudRepository
/**
 * Data repository for {@link Credentials} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CredentialsDao extends CrudRepository<Credentials, String> {

    List<Credentials> findRegistryCredentialsByUserId(Long userId)

    List<Credentials> findRegistryCredentialsByWorkspaceId(Long workspaceId)

}
