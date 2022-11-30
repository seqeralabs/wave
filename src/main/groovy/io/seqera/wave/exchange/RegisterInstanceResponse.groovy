package io.seqera.wave.exchange

import groovy.transform.CompileStatic

/**
 * Model the response for a remote service instance to register
 * itself as Wave credentials provider
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegisterInstanceResponse {
    String publicKey
}
