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

package io.seqera.wave.service.blob.impl


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.blob.TransferStrategy
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implements {@link TransferStrategy} that runs s5cmd using a docker
 * container. Meant for development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Singleton
@Requires(missingProperty = 'wave.build.k8s')
@Requires(property = 'wave.blobCache.enabled', value = 'true')
class DockerTransferStrategy implements TransferStrategy {

    @Inject
    private BlobCacheConfig blobConfig

    @Override
    void launchJob(String jobName, List<String> command) {
        // create a unique name for the container
        createProcess(command, jobName)
                .start()
    }

    protected ProcessBuilder createProcess(List<String> command, String name) {
        // compose the docker command
        final cli = new ArrayList<String>(10)
        cli.add('docker')
        cli.add('run')
        cli.add('--name')
        cli.add(name)
        cli.add('-e')
        cli.add('AWS_ACCESS_KEY_ID')
        cli.add('-e')
        cli.add('AWS_SECRET_ACCESS_KEY')
        cli.add(blobConfig.s5Image)
        cli.addAll(command)
        log.debug "Transfer docker command: ${cli.join(' ')}\n"

        // proc builder
        final builder = new ProcessBuilder()
        builder.environment().putAll(blobConfig.getEnvironment())
        builder.command(cli)
        builder.redirectErrorStream(true)
        return builder
    }


}
