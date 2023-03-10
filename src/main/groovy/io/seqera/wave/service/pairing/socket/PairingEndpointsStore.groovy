package io.seqera.wave.service.pairing.socket

import java.time.Duration

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton

@Singleton
@CompileStatic
class PairingEndpointsStore extends AbstractCacheStore<String> {

    PairingEndpointsStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<String>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-pairing-endpoints/v1:'
    }

    @Override
    protected Duration getDuration() {
        return Duration.ofDays(365)
    }
}
