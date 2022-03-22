package io.seqera

import io.seqera.config.DefaultConfiguration
import io.seqera.config.Registry
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import spock.lang.Shared

/**
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 *     This trait allows to wake up a temporal docker registry (without auth) and once started push an image
 *     from docker.io
 */
trait DockerRegistryContainer {

    @Shared
    static GenericContainer testcontainers = new GenericContainer(DockerImageName.parse("registry:2"))
            .withExposedPorts(5000)
            .waitingFor(
                    Wait.forLogMessage(".*listening on .*\\n", 1)
            );

    String getRegistryURL(){
        int port = testcontainers.firstMappedPort
        String url = "$testcontainers.containerIpAddress:$port"
        url
    }

    void initRegistryContainer(DefaultConfiguration defaultConfiguration){
        testcontainers.start()

        "docker pull hello-world".execute().waitForOrKill(2000)
        "docker tag hello-world $registryURL/hello-world".execute().waitForOrKill(1000)
        "docker push $registryURL/hello-world".execute().waitForOrKill(1000)
        "docker tag hello-world $registryURL/library/hello-world".execute().waitForOrKill(1000)
        "docker push $registryURL/library/hello-world".execute().waitForOrKill(1000)

        sleep 500 //let docker store the push

        defaultConfiguration.registries.push(
                new Registry(
                        name: 'test',
                        host: registryURL,
                        auth: null
                )
        )
    }
}