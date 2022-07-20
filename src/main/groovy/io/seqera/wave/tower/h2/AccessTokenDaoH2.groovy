package io.seqera.wave.tower.h2

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.seqera.wave.tower.AccessTokenDao

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env='h2')
@JdbcRepository(dialect = Dialect.H2)
interface AccessTokenDaoH2 extends AccessTokenDao{
}
