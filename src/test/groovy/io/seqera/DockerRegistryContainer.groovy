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
            .withPrivilegedMode(true)
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
        assert testcontainers.execInContainer("apk","add", "docker","bash").exitCode==0
        assert testcontainers.execInContainer("sh","-c","dockerd &").exitCode==0
        assert testcontainers.execInContainer("docker","pull","hello-world").exitCode==0
        assert testcontainers.execInContainer("docker","tag","hello-world","localhost:5000/hello-world").exitCode==0
        assert testcontainers.execInContainer("docker","push","localhost:5000/hello-world").exitCode==0
        assert testcontainers.execInContainer("docker","tag","hello-world","localhost:5000/library/hello-world").exitCode==0
        assert testcontainers.execInContainer("docker","push","localhost:5000/library/hello-world").exitCode==0

        def registry = new DefaultConfiguration.RegistryConfiguration()
        registry.name= 'test'
        registry.host= registryURL
        registry.auth= null
        defaultConfiguration.registries.push(registry)
    }
}
