package io.seqera.wave.service.pairing.socket.msg

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
@JsonSubTypes([
        @JsonSubTypes.Type(value = ProxyHttpRequest.class, name = "proxy-http-request"),
        @JsonSubTypes.Type(value = ProxyHttpResponse.class, name = "proxy-http-response"),
        @JsonSubTypes.Type(value = PairingResponse.class, name = "pairing-response"),
        @JsonSubTypes.Type(value = PairingHeartbeat.class, name = "pairing-heartbeat")
])
interface PairingMessage {
    String getMsgId()
}
