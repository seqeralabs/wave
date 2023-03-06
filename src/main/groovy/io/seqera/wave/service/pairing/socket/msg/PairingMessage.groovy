package io.seqera.wave.service.pairing.socket.msg

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

/**
 * Define the interface of a pairing message
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes([
    @JsonSubTypes.Type(value = PairingSend.class, name = "wave-send"),
    @JsonSubTypes.Type(value = PairingReply.class, name = "wave-reply"),
    @JsonSubTypes.Type(value = PairingHeartbeat.class, name = "wave-heartbeat")
])
interface PairingMessage extends Serializable {
    String getMsgId()
}
