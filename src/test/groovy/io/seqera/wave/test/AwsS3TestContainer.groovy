/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

import java.time.Duration

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
/**
 * Trait for AWS S3 test container
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
trait AwsS3TestContainer {
    private static final Logger log = LoggerFactory.getLogger(AwsS3TestContainer)

    static GenericContainer awsS3Container

    static {
        log.debug "Starting AWS S3 test container"
        awsS3Container = new GenericContainer<>("localstack/localstack:3.8.0")
                .withExposedPorts(4566)
                .withEnv("SERVICES", "s3")
                .withEnv("DEFAULT_REGION", "us-east-1")
                .withEnv("EDGE_PORT", "4566")
                .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200))
                .withStartupTimeout(Duration.ofMinutes(1))

        awsS3Container.start()
        log.debug "Started AWS S3 test container"
    }
    
    String getAwsS3HostName(){
        awsS3Container.getHost()
    }

    String getAwsS3Port(){
        awsS3Container.getMappedPort(4566)
    }
}
