package io.seqera

import io.seqera.config.DefaultConfiguration

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

        "docker pull hello-world".execute().waitForOrKill(4000)
        "docker tag hello-world $registryURL/hello-world".execute().waitForOrKill(2000)
        "docker push $registryURL/hello-world".execute().waitForOrKill(2000)
        "docker tag hello-world $registryURL/library/hello-world".execute().waitForOrKill(2000)
        "docker push $registryURL/library/hello-world".execute().waitForOrKill(2000)

        sleep 1500 //let docker store the push

        def registry = new DefaultConfiguration.RegistryConfiguration()
        registry.name= 'test'
        registry.host= registryURL
        registry.auth= null
        defaultConfiguration.registries.push(registry)
    }
}
