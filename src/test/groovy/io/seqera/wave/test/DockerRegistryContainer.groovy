/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.test

import spock.lang.Shared

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

    void initRegistryContainer(){
        testcontainers0.start()
        assert testcontainers0.execInContainer("apk","add", "docker","bash").exitCode==0
        // TEMPORARY: --iptables=false until seqeralabs/sched#1858 is deployed. Alpine's dockerd uses legacy
        // iptables and exits at startup when the host kernel has not loaded iptable_nat, as on sched GitHub
        // Actions runner VMs. This engine only pulls from Docker Hub and pushes to localhost:5000, so it
        // needs no NAT rules.
        assert testcontainers0.execInContainer("sh","-c","dockerd --iptables=false &").exitCode==0
        assert testcontainers0.execInContainer("docker","pull","hello-world@sha256:53641cd209a4fecfc68e21a99871ce8c6920b2e7502df0a20671c6fccc73a7c6").exitCode==0
        assert testcontainers0.execInContainer("docker","tag","hello-world@sha256:53641cd209a4fecfc68e21a99871ce8c6920b2e7502df0a20671c6fccc73a7c6","localhost:5000/hello-world").exitCode==0
        assert testcontainers0.execInContainer("docker","tag","hello-world@sha256:53641cd209a4fecfc68e21a99871ce8c6920b2e7502df0a20671c6fccc73a7c6","localhost:5000/library/hello-world").exitCode==0
        assert testcontainers0.execInContainer("docker","push","localhost:5000/hello-world").exitCode==0
        assert testcontainers0.execInContainer("docker","push","localhost:5000/library/hello-world").exitCode==0

    }
}
