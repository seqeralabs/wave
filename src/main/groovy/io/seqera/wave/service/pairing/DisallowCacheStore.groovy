package io.seqera.wave.service.pairing

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton

/**
 * Track and block pairing endpoints permanent failing
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class DisallowCacheStore extends AbstractCacheStore<Endpoint> {

    @Value('${wave.pairing.blockPeriod:12h}')
    private Duration blockPeriod

    DisallowCacheStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<Endpoint>() {})
    }

    @Override
    protected String getPrefix() {
        return 'endpoint-disallows/v1:'
    }

    @Override
    protected Duration getDuration() {
        return null
    }

    @Canonical
    static class Endpoint {
        final String endpoint
        final Instant createdAt
        final Instant modifiedAt
        final String error
    }

    void trackFailure(String endpoint, Throwable error) {
        final now = Instant.now()
        def entry = this.get(endpoint)
        if( entry==null ) {
            // create a new entry
            entry = new Endpoint(endpoint, now, now, error.message)
        }
        else {
            // update the entry
            entry = new Endpoint(entry.endpoint, entry.createdAt, now, error.message)
        }
        this.put(endpoint, entry)
    }

    boolean isBlocked(String endpoint) {
        isBlocked0(this.get(endpoint), blockPeriod)
    }

    static boolean isBlocked0(Endpoint entry, Duration period) {
        if( !entry )
            return false
        return entry.createdAt.plus(period) < Instant.now()
    }

}
