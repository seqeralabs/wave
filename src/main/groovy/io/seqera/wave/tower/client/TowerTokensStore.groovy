package io.seqera.wave.tower.client

import groovy.transform.CompileStatic
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider

import java.time.Duration

import jakarta.inject.Singleton

@Singleton
@CompileStatic
class TowerTokensStore extends AbstractCacheStore<JwtAuth> {


    TowerTokensStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<JwtAuth>() {})
    }

    @Override
    protected String getPrefix() {
        return "tower-auth-tokens/v1:"
    }

    @Override
    protected Duration getDuration() {
        return Duration.ofHours(1)
    }
}
