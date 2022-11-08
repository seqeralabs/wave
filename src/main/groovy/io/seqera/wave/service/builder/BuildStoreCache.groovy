package io.seqera.wave.service.builder

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.EncodingStrategyFactory
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton

/**
 *  Implements a cache {@link BuildStore}
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class BuildStoreCache extends AbstractCacheStore<BuildResult> implements BuildStore{

    private Duration duration
    private Duration delay
    private Duration timeout

    BuildStoreCache(@Value('${wave.build.status.duration:`1d`}')Duration duration,
                    @Value('${wave.build.status.delay:5s}')Duration delay,
                    @Value('${wave.build.timeout:5m}')Duration timeout,
                    EncodingStrategyFactory encodingStrategyFactory,
                    CacheProvider<String,String> delegate) {
        super(encodingStrategyFactory, delegate)
        this.duration = duration
        this.delay = delay
        this.timeout = timeout
        log.info "Build store - duration=$duration; timeout=$timeout; delay=$delay"
    }


    Duration getDelay() { delay }

    Duration getTimeout() { timeout }

    @Override
    protected String getPrefix() {
        return 'wave-build/v1:'
    }

    @Override
    BuildResult getBuild(String imageName) {
        return get(imageName)
    }

    @Override
    void storeBuild(String imageName, BuildResult result) {
        storeBuild(imageName, result, duration)
    }

    @Override
    void storeBuild(String imageName, BuildResult result, Duration ttl) {
        put(imageName, result, ttl)
    }

    @Override
    boolean storeIfAbsent(String imageName, BuildResult build) {
        putIfAbsent(imageName, build)
    }

    @Override
    void removeBuild(String imageName) {
        remove(imageName)
    }
}
