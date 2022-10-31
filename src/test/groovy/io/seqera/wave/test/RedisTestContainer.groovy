package io.seqera.wave.test


import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
trait RedisTestContainer {

    private static final Logger log = LoggerFactory.getLogger(RedisTestContainer)

    static GenericContainer redisContainer

    static {
        log.debug "Starting Redis test container"
        redisContainer = new GenericContainer(DockerImageName.parse("redis:7.0.4-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
        redisContainer.start()
        log.debug "Started Redis test container"
    }


    String getRedisHostName(){
        redisContainer.getHost()
    }

    String getRedisPort(){
        redisContainer.getMappedPort(6379)
    }

    def cleanupSpec(){
        redisContainer.stop()
    }
}
