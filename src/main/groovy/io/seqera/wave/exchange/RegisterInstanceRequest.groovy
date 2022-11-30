package io.seqera.wave.exchange

import groovy.transform.CompileStatic

/**
 * Model the request for a remote service instance to register
 * itself as Wave credentials provider
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class RegisterInstanceRequest {

    String service
    String instanceId
}
