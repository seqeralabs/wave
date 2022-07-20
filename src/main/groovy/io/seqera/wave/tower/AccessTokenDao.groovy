package io.seqera.wave.tower

import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository

/**
 * Data repository for {@link AccessToken} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface AccessTokenDao extends CrudRepository<AccessToken, Long> {
}
