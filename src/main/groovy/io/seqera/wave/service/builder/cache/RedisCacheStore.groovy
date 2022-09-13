package io.seqera.wave.service.builder.cache

import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.lettuce.core.api.StatefulRedisConnection
import io.lettuce.core.pubsub.RedisPubSubAdapter
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.util.JacksonHelper
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class RedisCacheStore implements CacheStore<String, BuildRequest> {

    private static final String BUILDS_CHANNEL = 'builds'
    private StatefulRedisPubSubConnection<String,String> pubSubConn
    private StatefulRedisConnection<String,String> senderConn
    private Duration duration = Duration.ofHours(1)
    private ConcurrentHashMap<String, CountDownLatch> watchers = new ConcurrentHashMap<>()

    RedisCacheStore(StatefulRedisConnection<String,String> senderConn, StatefulRedisPubSubConnection < String, String > pubSubConn) {
        this.senderConn = senderConn
        this.pubSubConn = pubSubConn
        this.pubSubConn.addListener( new RedisPubSubAdapter<String, String>() {
            @Override
            void message(String channel, String message) {
                log.debug "+++ got message=$message; channel=$channel"
                if( channel!=BUILDS_CHANNEL ) return
                final latch = watchers.putIfAbsent(message, new CountDownLatch(2))
                if( latch!=null )
                    latch.countDown()
            }
        })
        this.pubSubConn.sync().subscribe(BUILDS_CHANNEL)
    }


    @Override
    boolean containsKey(String key) {
        // this does not work 
        // return senderConn.sync().exists(key)
        return false
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
        senderConn.sync().publish(BUILDS_CHANNEL, key)
    }

    @Override
    BuildRequest await(String key) {
        final latch = watchers.get(key)
        if( latch ) {
            latch.await()
            return get(key)
        }
        else
            return null
    }

}
