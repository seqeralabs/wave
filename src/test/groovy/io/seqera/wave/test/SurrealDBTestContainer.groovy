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

import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.output.Slf4jLogConsumer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
trait SurrealDBTestContainer {
    private static final def LOGGER = LoggerFactory.getLogger(SurrealDBTestContainer.class);

    @Shared
    static GenericContainer surrealContainer = new GenericContainer(DockerImageName.parse("surrealdb/surrealdb:v1.4.2"))
            .withExposedPorts(8000)
            .withCommand("start","--user", "root", "--pass", "root", '--log', 'debug')
            .waitingFor(
                    Wait.forLogMessage(".*Started web server on .*\\n", 1)
            )
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))

    void restartDb(){
        if( surrealContainer.running)
            surrealContainer.stop()
        surrealContainer.start()
    }

    String getSurrealHostName(){
        surrealContainer.getHost()
    }

    String getSurrealPort(){
        surrealContainer.getMappedPort(8000)
    }
}
