package io.seqera.wave.test

import spock.lang.Shared

import io.micronaut.context.ApplicationContext
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
/**
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 *     This trait allows to wake up a temporal docker registry (without auth) and once started push an image
 *     from docker.io
 */
trait DockerRegistryContainer extends BaseTestContainerRegistry {

    @Shared
    static GenericContainer testcontainers0 = new GenericContainer(DockerImageName.parse("registry:2"))
            .withExposedPorts(5000)
            .withPrivilegedMode(true)
            .waitingFor(
                    Wait.forLogMessage(".*listening on .*\\n", 1)
            );

    @Override
    GenericContainer getTestcontainers() { testcontainers0 }

    void initRegistryContainer(ApplicationContext applicationContext){
        testcontainers0.start()
        assert testcontainers0.execInContainer("apk","add", "docker","bash").exitCode==0
        assert testcontainers0.execInContainer("sh","-c","dockerd &").exitCode==0
        assert testcontainers0.execInContainer("docker","pull","hello-world").exitCode==0
        assert testcontainers0.execInContainer("docker","tag","hello-world","localhost:5000/hello-world").exitCode==0
        assert testcontainers0.execInContainer("docker","tag","hello-world","localhost:5000/library/hello-world").exitCode==0
        assert testcontainers0.execInContainer("docker","push","localhost:5000/hello-world").exitCode==0
        assert testcontainers0.execInContainer("docker","push","localhost:5000/library/hello-world").exitCode==0

    }
}
