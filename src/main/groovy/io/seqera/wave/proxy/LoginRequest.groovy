package io.seqera.wave.proxy

import groovy.transform.Canonical
import groovy.transform.CompileStatic

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class LoginRequest {
    String username
    String password
}
