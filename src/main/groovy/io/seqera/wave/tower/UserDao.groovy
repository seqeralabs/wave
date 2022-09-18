package io.seqera.wave.tower

import io.micronaut.data.repository.CrudRepository
/**
 * Data repository for {@link User} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface UserDao extends CrudRepository<User, Long> {
}
