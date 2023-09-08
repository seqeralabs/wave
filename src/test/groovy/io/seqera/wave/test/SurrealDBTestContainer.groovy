/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
    static GenericContainer surrealContainer = new GenericContainer(DockerImageName.parse("surrealdb/surrealdb:1.0.0-beta.8"))
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
