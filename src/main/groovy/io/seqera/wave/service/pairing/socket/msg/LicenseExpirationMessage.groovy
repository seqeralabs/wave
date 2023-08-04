package io.seqera.wave.service.pairing.socket.msg

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.ToString
/**
 * ExpirationMessage model
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@ToString(includePackage = false, includeNames = true)
class LicenseExpirationMessage implements PairingMessage{
    String msgId
    String message
    Instant expiration
}
