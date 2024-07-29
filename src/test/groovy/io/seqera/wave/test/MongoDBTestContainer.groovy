/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
/**
 * trait for MongoDB test container
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
trait MongoDBTestContainer{
    private static final Logger log = LoggerFactory.getLogger(MongoDBTestContainer)

    static GenericContainer mongoDBContainer


    String getMongoDBHostName(){
        mongoDBContainer.getHost()
    }

    String getMongoDBPort(){
        mongoDBContainer.getMappedPort(27017)
    }

    def setupSpec() {
        log.debug "Starting Redis test container"
        mongoDBContainer = new GenericContainer<>(
                DockerImageName.parse("mongo:7.0.12"))
                .withExposedPorts(27017)
                .waitingFor(Wait.forLogMessage(".*Waiting for connections.*\\n", 1))

        mongoDBContainer.start()
        log.debug "Started Redis test container"
    }

    def cleanupSpec(){
        mongoDBContainer.stop()
    }
}

