package io.seqera.wave.tower


import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

/**
 * Data repository for {@link Credentials} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CredentialsDao extends CrudRepository<Credentials, String> {

    List<Credentials> findRegistryCredentialsByUser(Long userId)

    List<Credentials> findRegistryCredentialsByWorkspaceId(Long workspaceId)

}
