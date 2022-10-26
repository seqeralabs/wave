package io.seqera.wave.test

import spock.lang.Shared

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile


/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
trait RedisTestContainer {

    @Shared
    static GenericContainer redisContainer = new GenericContainer(DockerImageName.parse("redis:7.0.4-alpine"))
            .withExposedPorts(6379)
            .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1));


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
