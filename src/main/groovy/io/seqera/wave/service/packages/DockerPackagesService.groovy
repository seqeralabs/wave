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

package io.seqera.wave.service.packages

import java.nio.file.Path

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.PackagesConfig
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@Requires(missingProperty = 'wave.build.k8s')
@CompileStatic
class DockerPackagesService extends AbstractPackagesService {

    @Inject
    private PackagesConfig config

    protected List<String> dockerWrapper(Path workDir) {

        final wrapper = ['docker','run', '--rm']

        // scan work dir
        wrapper.add('-w')
        wrapper.add(workDir.toString())

        wrapper.add('-v')
        wrapper.add("$workDir:$workDir:rw".toString())

        wrapper.add(config.condaImage)

        return wrapper
    }

    @Override
    protected void run(List<String> cmd, Path workDir) {

        //launch scanning
        final command = dockerWrapper(workDir) + cmd
        log.debug("Conda fetcher command: ${command.join(' ')}")

        final process = new ProcessBuilder()
                .command(command)
                .redirectErrorStream(true)
                .start()

        final exitCode = process.waitFor()
        if ( exitCode != 0 ) {
            throw new IllegalStateException("Conda fetcher failed: ${exitCode} - cause: ${process.text}")
        }

    }
}
