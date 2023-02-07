package io.seqera.wave.service.pairing

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton

/**
 * Implements a cache store for {@link PairingRecord}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingCacheStore extends AbstractCacheStore<PairingRecord> {

    /**
     * How long the pairing key can stay in the cache store before is evicted
     */
    @Value('${wave.pairing-key.duration:`30d`}')
    private Duration duration

    /**
     * The period of time after which the token should be renewed
     */
    @Value('${wave.pairing-key.lease:`1d`}')
    private Duration lease

    PairingCacheStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<PairingRecord>() {})
    }

    @PostConstruct
    private void init() {
        log.info "Creating Pairing cache store ― duration=${duration}; lease=${lease}"
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
