package io.seqera.wave.service.pairing.socket.msg

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import io.seqera.wave.exchange.PairingResponse

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes([
        @JsonSubTypes.Type(value = UserRequest.class, name = "user-request"),
        @JsonSubTypes.Type(value = UserResponse.class, name = "user-response"),
        @JsonSubTypes.Type(value = PairingResponse.class, name = "pairing-response"),
])
interface PairingPayload {
}
