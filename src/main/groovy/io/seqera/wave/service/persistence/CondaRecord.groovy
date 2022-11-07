package io.seqera.wave.service.persistence

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

/**
 * Model a conda lock file
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode
@CompileStatic
class CondaRecord {
    String id
    String lockFile
}
