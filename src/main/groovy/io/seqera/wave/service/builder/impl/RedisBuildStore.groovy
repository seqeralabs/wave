package io.seqera.wave.service.builder.impl

import java.time.Duration
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.lettuce.core.SetArgs
import io.lettuce.core.api.StatefulRedisConnection
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
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
class RedisBuildStore implements BuildStore {

    private StatefulRedisConnection<String,String> senderConn

    @Value('${wave.build.status.duration:`1d`}')
    private Duration duration

    @Value('${wave.build.status.delay:5s}')
    private Duration delay

    @Value('${wave.build.timeout:5m}')
    private Duration timeout

    Duration getDelay() { delay }

    Duration getTimeout() { timeout }

    RedisBuildStore(StatefulRedisConnection<String,String> senderConn) {
        this.senderConn = senderConn
    }

    @PostConstruct
    void init() {
        log.info "Redis build store - duration=$duration; timeout=$timeout; delay=$delay"
    }

    private String key(String name) {  'wave-build/v1:' + name }

    @Override
    BuildResult getBuild(String imageName) {
        final json = senderConn.sync().get(key(imageName))
        if( json==null )
            return null
        return JacksonHelper.fromJson(json, BuildResult)
    }

    @Override
    void storeBuild(String imageName, BuildResult request) {
        final json = JacksonHelper.toJson(request)
        // once created the token the user has `Duration` time to pull the layers of the image
        senderConn.sync().psetex(key(imageName), duration.toMillis(), json)
    }

    @Override
    void storeBuild(String imageName, BuildResult request, Duration ttl) {
        final json = JacksonHelper.toJson(request)
        // once created the token the user has `Duration` time to pull the layers of the image
        senderConn.sync().psetex(key(imageName), ttl.toMillis(), json)
    }

    @Override
    boolean storeIfAbsent(String imageName, BuildResult build) {
        final json = JacksonHelper.toJson(build)
        final SetArgs args = SetArgs.Builder.ex(duration).nx()
        final result = senderConn.sync().set(key(imageName), json, args)
        return result == 'OK'
    }

    @Override
    void removeBuild(String imageName) {
        senderConn.sync().del(key(imageName))
    }
}
