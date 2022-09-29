package io.seqera.wave.test

import spock.lang.Shared

import org.testcontainers.containers.GenericContainer
import org.testcontainers.utility.DockerImageName

/**
 *
 * @author Jorge Aguilera <jorge.aguilera@seqera.io>
 */
trait SurrealDBTestContainer {

    @Shared
    static GenericContainer surrealContainer = new GenericContainer(DockerImageName.parse("surrealdb/surrealdb:latest"))
            .withExposedPorts(8000)
            .withCommand("start","--user", "root", "--pass", "root", '--log', 'debug')


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
