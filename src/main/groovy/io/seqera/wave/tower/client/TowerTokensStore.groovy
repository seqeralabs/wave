package io.seqera.wave.tower.client

import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.model.TowerTokens
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider

import java.time.Duration

class TowerTokensStore extends AbstractCacheStore<TowerTokens> {


    TowerTokensStore(CacheProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<TowerTokens>() {})
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
