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

import io.micronaut.context.ApplicationContext
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
trait SecureDockerRegistryContainer extends BaseTestContainerRegistry {

    @Shared
    static GenericContainer testcontainers0 = new GenericContainer(DockerImageName.parse("registry:2"))
            .withExposedPorts(5000)
            .withPrivilegedMode(true)
            .withCopyFileToContainer(MountableFile.forClasspathResource ("/registry.password"),"/auth/")
            .withEnv(REGISTRY_AUTH: "htpasswd",
                    REGISTRY_AUTH_HTPASSWD_REALM: "Registry",
                    REGISTRY_AUTH_HTPASSWD_PATH: "/auth/registry.password")
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
        assert testcontainers0.execInContainer("docker","login","localhost:5000","-u","test","--password","test").exitCode==0
        assert testcontainers0.execInContainer("docker","push","localhost:5000/hello-world").exitCode==0
        assert testcontainers0.execInContainer("docker","push","localhost:5000/library/hello-world").exitCode==0

    }
}
