package io.seqera.wave.tower

import io.micronaut.data.annotation.Join
import io.micronaut.data.repository.CrudRepository
/**
 * Data repository for {@link AccessToken} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface AccessTokenDao extends CrudRepository<AccessToken, Long> {

    @Join(value = "user", type = Join.Type.FETCH)
    abstract Optional<AccessToken> findById(Long id)

}
