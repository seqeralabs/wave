/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.test

import spock.lang.Shared

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

    @Shared
    static GenericContainer redisContainer;

    String getRedisHostName(){
        redisContainer.getHost()
    }

    String getRedisPort(){
        redisContainer.getMappedPort(6379).toString()
    }

    def setupSpec() {
        redisContainer = new GenericContainer(DockerImageName.parse("redis:7.0.4-alpine"))
                .withExposedPorts(6379)
                .waitingFor(Wait.forLogMessage(".*Ready to accept connections.*\\n", 1))
        // starting redis
        redisContainer.start()
        log.debug "Started Redis test container"
        // Set Redis host and port as system properties
        // those properties are accessed by the RedisTestFactory class 
        System.setProperty("redis.host", redisContainer.getHost())
        System.setProperty("redis.port", redisContainer.getMappedPort(6379).toString())
    }

    def cleanupSpec(){
        log.debug "Stopping Redis test container"
        redisContainer.stop()
    }
}
