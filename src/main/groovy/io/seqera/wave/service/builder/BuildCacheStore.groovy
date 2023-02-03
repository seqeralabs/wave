package io.seqera.wave.service.builder

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Singleton
/**
 * Implements Cache store for {@link BuildResult}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class BuildCacheStore extends AbstractCacheStore<BuildResult> implements BuildStore {

    private Duration duration
    private Duration delay
    private Duration timeout

    BuildCacheStore(
            CacheProvider<String, String> provider,
            @Value('${wave.build.status.delay:5s}') Duration delay,
            @Value('${wave.build.timeout:5m}') Duration timeout,
            @Value('${wave.build.status.duration:`1d`}') Duration duration
    ) {
        super(provider, new MoshiEncodeStrategy<BuildResult>() {})
        this.duration = duration
        this.delay = delay
        this.timeout = timeout
        log.info "Creating Build cache store ― duration=$duration; delay=$delay; timeout=$timeout"
    }

    @Override
    protected String getPrefix() {
        return 'wave-build/v1:'
    }

    @Override
    protected Duration getDuration() {
        return duration
    }

    @Override
    Duration getTimeout() {
        return timeout
    }

    @Override
    Duration getDelay() {
        return delay
    }

    @Override
    BuildResult getBuild(String imageName) {
        return get(imageName)
    }

    @Override
    void storeBuild(String imageName, BuildResult result) {
        put(imageName, result)
    }

    @Override
    void storeBuild(String imageName, BuildResult result, Duration ttl) {
        put(imageName, result, ttl)
    }

    @Override
    boolean storeIfAbsent(String imageName, BuildResult build) {
        return putIfAbsent(imageName, build)
    }

    @Override
    void removeBuild(String imageName) {
        remove(imageName)
    }

}
