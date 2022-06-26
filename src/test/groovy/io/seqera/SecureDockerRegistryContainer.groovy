package io.seqera

import spock.lang.Shared

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import io.seqera.auth.ConfigurableAuthProvider
import io.seqera.auth.DockerAuthProvider
import io.seqera.config.RegistryBean
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile

/**
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 *     This trait allows to wake up a temporal docker registry (without auth) and once started push an image
 *     from docker.io
 */
trait SecureDockerRegistryContainer {

    @Shared
    static GenericContainer testcontainers = new GenericContainer(DockerImageName.parse("registry:2"))
            .withExposedPorts(5000)
            .withPrivilegedMode(true)
            .withCopyFileToContainer(MountableFile.forClasspathResource ("/registry.password"),"/auth/")
            .withEnv(REGISTRY_AUTH: "htpasswd",
                    REGISTRY_AUTH_HTPASSWD_REALM: "Registry",
                    REGISTRY_AUTH_HTPASSWD_PATH: "/auth/registry.password")
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
        assert testcontainers.execInContainer("docker","login","localhost:5000","-u","test","--password","test").exitCode==0
        assert testcontainers.execInContainer("docker","push","localhost:5000/hello-world").exitCode==0
        assert testcontainers.execInContainer("docker","push","localhost:5000/library/hello-world").exitCode==0

        RegistryBean registryBean = applicationContext.getBean(RegistryBean, Qualifiers.byName('test'))
        registryBean.name = "$testcontainers.containerIpAddress:$testcontainers.firstMappedPort"
        registryBean.host = registryURL
        ConfigurableAuthProvider authProvider =
                applicationContext.getBean(DockerAuthProvider, Qualifiers.byName('test')) as ConfigurableAuthProvider
        authProvider.authUrl = registryURL
    }
}
