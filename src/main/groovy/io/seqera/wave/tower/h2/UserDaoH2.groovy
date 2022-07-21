package io.seqera.wave.tower.h2

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.seqera.wave.tower.UserDao

/**
 * H2 Data repository for {@link io.seqera.wave.tower.User} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(env='h2')
@JdbcRepository(dialect = Dialect.H2)
interface UserDaoH2 extends UserDao{
}
