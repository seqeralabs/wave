package io.seqera.wave.tower.mysql

import io.micronaut.context.annotation.Requires
import io.micronaut.data.jdbc.annotation.JdbcRepository
import io.micronaut.data.model.query.builder.sql.Dialect
import io.seqera.wave.tower.AccessTokenDao

/**
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Requires(env='mysql')
@JdbcRepository(dialect = Dialect.MYSQL)
interface AccessTokenDaoMySql extends AccessTokenDao{
}
