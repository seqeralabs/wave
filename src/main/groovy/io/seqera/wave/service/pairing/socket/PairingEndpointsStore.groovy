package io.seqera.wave.service.pairing.socket

import java.time.Duration

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.websocket.WebSocketSession
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton

@Singleton
@CompileStatic
class PairingEndpointsStore extends AbstractCacheStore<Entry> {

    @Canonical
    static class Entry implements Serializable {
        String service
        String endpoint

        static Entry of(WebSocketSession session) {
            final endpoint = session.requestParameters.get('endpoint',String, null)
            final service = session.uriVariables.get('service',String, null)
            if( !endpoint ) throw new IllegalArgumentException("Missing Pairing endpoint")
            if( !service ) throw new IllegalArgumentException("Missing Pairing service")
            return new Entry(service, endpoint)
        }
    }

    /**
     * note this must be longer than wave websocket client heartbeat inteerval
     */
    @Value('${wave.pairing.token.duration:180s}')
    private Duration duration

    PairingEndpointsStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<Entry>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-pairing-endpoints/v1:'
    }

    @Override
    protected Duration getDuration() {
        return duration
    }

    void addWebsocketSession(WebSocketSession session) {
        this.biPut(session.id, Entry.of(session), duration)
    }

    void removeWebsocketSession(WebSocketSession session) {
        this.biRemove(session.id)
    }

    String getWebsocketSessionId(String service, String endpoint) {
        this.biKeyFind(new Entry(service,endpoint), false)
    }

    boolean hasWebsocketSession(String service, String endpoint) {
        return getWebsocketSessionId(service,endpoint) != null
    }

}
