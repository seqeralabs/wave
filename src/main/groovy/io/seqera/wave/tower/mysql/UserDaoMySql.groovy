package io.seqera.wave.tower.mysql

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.seqera.wave.tower.UserDao

/**
 * MySQL Data repository for {@link io.seqera.wave.tower.User} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env='mysql')
@JdbcRepository(dialect = Dialect.MYSQL)
interface UserDaoMySql extends UserDao {
}
