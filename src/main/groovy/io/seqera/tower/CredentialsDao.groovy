package io.seqera.tower

import io.micronaut.core.annotation.Nullable
import io.micronaut.data.annotation.Query
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

/**
 * Data repository for {@link Credentials} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@JdbcRepository(dialect = Dialect.H2)
interface CredentialsDao extends CrudRepository<Credentials, String> {

    @Query("select * from tw_credentials c where c.provider = 'container-reg' and (c.deleted is null or c.deleted = false) and c.user_id = :userId and c.workspace_id is null")
    List<Credentials> findRegistryCredentialsByUser(Long userId)

    @Query("select * from tw_credentials c where c.provider = 'container-reg' and (c.deleted is null or c.deleted = false) and c.workspace_id = :workspaceId")
    List<Credentials> findRegistryCredentialsByWorkspaceId(Long workspaceId)

}
