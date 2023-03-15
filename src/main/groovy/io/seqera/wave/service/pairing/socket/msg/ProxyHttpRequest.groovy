package io.seqera.wave.service.pairing.socket.msg

import groovy.transform.Canonical
import groovy.transform.ToString
import io.micronaut.http.HttpMethod

/**
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
class ProxyHttpRequest implements PairingMessage {
    String msgId
    String method
    String uri
    String bearerAuth
    String body
    Map<String, List<String>> headers
}
