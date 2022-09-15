package io.seqera.wave.service.builder.cache

import java.time.Duration
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.lettuce.core.api.StatefulRedisConnection
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(property = 'redis.uri')
@Singleton
@CompileStatic
class RedisCacheStore implements CacheStore {

    private StatefulRedisConnection<String,String> senderConn

    @Value('${wave.build.status.duration:`1d`}')
    private Duration duration

    @Value('${wave.build.status.delay:5s}')
    private Duration delay

    @Value('${wave.build.timeout:5m}')
    private Duration timeout

    RedisCacheStore(StatefulRedisConnection<String,String> senderConn) {
        log.info "Creating Redis build store - duration=$duration; timeout=$timeout; delay=$delay"
        this.senderConn = senderConn
    }

    @Override
    boolean containsKey(String key) {
        return senderConn.sync().get(key) != null
    }

    @Override
    BuildRequest get(String key) {
        final json = senderConn.sync().get(key)
        if( json==null )
            return null
        return JacksonHelper.fromJson(json, BuildRequest)
    }

    @Override
    void put(String key, BuildRequest value) {
        def json = JacksonHelper.toJson(value)
        // once created the token the user has `Duration` time to pull the layers of the image
        senderConn.sync().psetex(key, duration.toMillis(), json)
    }

    @Override
    CompletableFuture<BuildRequest> await(String key) {
        final payload = senderConn.sync().get(key)
        if( !payload )
            return null
        CompletableFuture<BuildRequest>.supplyAsync(() -> awaitCompletion(key,payload))
    }

    protected BuildRequest awaitCompletion(String key, String payload) {
        final beg = System.currentTimeMillis()
        final max = (timeout.toMillis() * 0.10) as long
        while( true ) {
            final current = JacksonHelper.fromJson(payload, BuildRequest)
            if( current.finished )
                return current
            final delta = System.currentTimeMillis()-beg
            if( delta > max)
                throw new BadRequestException("Build of container '$key' timed out")
            // sleep a bit
            Thread.sleep(delay.toMillis())
            // fetch the build status again
            payload = senderConn.sync().get(key)
        }
    }

}
