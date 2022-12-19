package io.seqera.wave.tower

import javax.validation.constraints.NotNull
import javax.validation.constraints.Size

import groovy.transform.CompileStatic
import groovy.transform.ToString
/**
 * Model a tower user
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false, includes = 'id,userName,email')
@CompileStatic
class User {

    Long id

    @NotNull
    @Size(max = 40)
    String userName

    @NotNull
    @Size(max = 255)
    String email

}
