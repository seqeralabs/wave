package io.seqera.wave.service.builder.impl

import java.time.Duration
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.lettuce.core.api.StatefulRedisConnection
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.BuildStore
import io.seqera.wave.util.JacksonHelper
import jakarta.inject.Singleton
/**
 *  Implement Redis based version of {@link BuildStore}
 *  
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(property = 'redis.uri')
@Singleton
@CompileStatic
class RedisCacheStore implements BuildStore {

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
    boolean hasBuild(String imageName) {
        return senderConn.sync().get(imageName) != null
    }

    @Override
    BuildResult getBuild(String imageName) {
        final json = senderConn.sync().get(imageName)
        if( json==null )
            return null
        return JacksonHelper.fromJson(json, BuildResult)
    }

    @Override
    void storeBuild(String imageName, BuildResult request) {
        def json = JacksonHelper.toJson(request)
        // once created the token the user has `Duration` time to pull the layers of the image
        senderConn.sync().psetex(imageName, duration.toMillis(), json)
    }

    @Override
    CompletableFuture<BuildResult> awaitBuild(String imageName) {
        final payload = senderConn.sync().get(imageName)
        if( !payload )
            return null
        CompletableFuture<BuildResult>.supplyAsync(() -> awaitCompletion0(imageName,payload))
    }

    protected BuildResult awaitCompletion0(String key, String payload) {
        final beg = System.currentTimeMillis()
        // add 10% delay gap to prevent race condition with timeout expiration
        final max = (timeout.toMillis() * 1.10) as long
        while( true ) {
            // de-serialise the json payload
            final current = JacksonHelper.fromJson(payload, BuildResult)
            // check is completed
            if( current.done() )
                return current
            // check if it's timed out
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
