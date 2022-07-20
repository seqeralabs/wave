package io.seqera.wave.tower.mysql

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.micronaut.data.repository.CrudRepository
import io.seqera.wave.tower.Secret
import io.seqera.wave.tower.SecretDao

/**
 * Data repository for {@link io.seqera.wave.tower.Secret} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env='mysql')
@JdbcRepository(dialect = Dialect.H2)
interface SecretDaoMySql extends SecretDao{

}
