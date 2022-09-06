package io.seqera.wave.test

import spock.lang.Shared

import com.redis.testcontainers.RedisContainer

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
trait RedisTestContainer {

    @Shared
    static RedisContainer redisContainer = new RedisContainer(
            RedisContainer.DEFAULT_IMAGE_NAME.withTag(RedisContainer.DEFAULT_TAG)).withKeyspaceNotifications()

    void startRedis(){
        redisContainer.start()
    }

    void restartRedis(){
        if( redisContainer.running)
            redisContainer.stop()
        redisContainer.start()
    }

    String getRedisHostName(){
        redisContainer.getHost()
    }

    String getRedisPort(){
        redisContainer.getMappedPort(6379)
    }
}
