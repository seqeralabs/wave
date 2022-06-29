package io.seqera.tower

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

/**
 * Data repository for {@link AccessToken} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@JdbcRepository(dialect = Dialect.H2)
interface AccessTokenDao extends CrudRepository<AccessToken, Long> {
}
