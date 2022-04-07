package io.seqera.testcontainers

import spock.lang.Shared

import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.utility.DockerImageName

trait AwsContainer {

    @Shared
    static GenericContainer localstack = new GenericContainer(
            DockerImageName.parse("localstack/localstack:0.14.1"))
            .withEnv("SERVICES","s3")
            .withExposedPorts(4566)
            .waitingFor(
                    Wait.forLogMessage(".*Ready\\.\n", 1)
            );

    void initAwsContainer() {
        localstack.start()
    }

    void stopAwsContainer(){
        localstack.stop()
    }

    String endpointFor(){
        int port = localstack.firstMappedPort
        String url = "http://$localstack.containerIpAddress:$port"
        url
    }
}
