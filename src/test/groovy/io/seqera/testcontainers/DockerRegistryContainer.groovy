package io.seqera.testcontainers

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.seqera.auth.ConfigurableAuthProvider
import io.seqera.auth.DockerAuthProvider
import io.seqera.auth.SimpleAuthProvider
import io.seqera.config.DefaultConfiguration
import io.seqera.config.Registry
import io.seqera.config.RegistryBean
import jakarta.inject.Inject
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
        String url = "http://$testcontainers.containerIpAddress:$port"
        url
    }

    void initRegistryContainer(ApplicationContext applicationContext){
        testcontainers.start()
        assert testcontainers.execInContainer("apk","add", "docker","bash").exitCode==0
        assert testcontainers.execInContainer("sh","-c","dockerd &").exitCode==0
        assert testcontainers.execInContainer("docker","pull","hello-world").exitCode==0
        assert testcontainers.execInContainer("docker","tag","hello-world","localhost:5000/hello-world").exitCode==0
        assert testcontainers.execInContainer("docker","tag","hello-world","localhost:5000/library/hello-world").exitCode==0
        assert testcontainers.execInContainer("docker","push","localhost:5000/hello-world").exitCode==0
        assert testcontainers.execInContainer("docker","push","localhost:5000/library/hello-world").exitCode==0

        RegistryBean registryBean = applicationContext.getBean(RegistryBean, Qualifiers.byName('test'))
        registryBean.name = "$testcontainers.containerIpAddress:$testcontainers.firstMappedPort"
        registryBean.host = registryURL
        applicationContext.registerSingleton(DockerAuthProvider, new SimpleAuthProvider(),Qualifiers.byName('test'))
    }
}
