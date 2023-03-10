package io.seqera.wave.service.pairing.socket.msg

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes([
        @JsonSubTypes.Type(value = ProxyGetRequest.class, name = "proxy-get-request"),
        @JsonSubTypes.Type(value = ProxyGetResponse.class, name = "proxy-get-response"),
        @JsonSubTypes.Type(value = PairingResponse.class, name = "pairing-response"),
        @JsonSubTypes.Type(value = PairingHeartbeat.class, name = "pairing-heartbeat")
])
interface PairingMessage {
    String getMsgId()
}
