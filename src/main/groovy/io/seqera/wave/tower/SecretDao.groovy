package io.seqera.wave.tower

import io.micronaut.data.repository.CrudRepository
/**
 * Data repository for {@link Secret} entity
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface SecretDao extends CrudRepository<Secret, String> {

}
