package io.seqera.wave.service.pairing.socket.msg

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import groovy.transform.Canonical
import groovy.transform.ToString
import io.seqera.wave.exchange.PairingResponse

/**
 *  Model a pairing command request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@ToString(includePackage = false, includeNames = true)
class PairingSend<T extends PairingPayload> implements PairingMessage {

    String msgId

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes([
            @JsonSubTypes.Type(value = UserRequest.class, name = "user-request"),
            @JsonSubTypes.Type(value = PairingResponse.class, name = "pairing-response"),
    ])
    T payload
}
