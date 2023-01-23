package io.seqera.wave.service.security

import java.time.Duration

import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
/**
 * Implements a cache store for {@link PairingRecord}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class PairingCacheStore extends AbstractCacheStore<PairingRecord> {

    @Value('${wave.pairing-keys.duration:`30d`}')
    private Duration duration

    PairingCacheStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<PairingRecord>() {})
    }

    @Override
    protected String getPrefix() {
        return 'pairing-keys/v1:'
    }

    /**
     * @return A duration representing the TTL of the entries in the cache
     */
    @Override
    protected Duration getDuration() {
        // note: the cache store should be modified to allow the support for
        // infinite duration using with null
        return duration
    }
}
