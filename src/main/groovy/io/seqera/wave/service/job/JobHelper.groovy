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

package io.seqera.wave.service.job

import java.nio.file.Files
import java.nio.file.Path

import groovy.json.JsonOutput
import groovy.util.logging.Slf4j
import static java.nio.file.StandardOpenOption.CREATE
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING
import static java.nio.file.StandardOpenOption.WRITE

/**
 * Helper class to handle jobs execution common logic
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
class JobHelper {

    /**
     * Await the {@code docker run --detach} process launching a job container, adopting the
     * existing container when it was already created by a previous launch attempt. Job queues are
     * at-least-once: a launch can be redelivered after the container was created, and the
     * deterministic container name makes the second {@code docker run} fail with a name conflict.
     *
     * @param process The {@code docker run} process
     * @param name The deterministic name of the job container
     * @param what A short description of the job used in the error message
     * @throws IllegalStateException when the container cannot be launched for any other reason
     */
    static void awaitDockerLaunch(Process process, String name, String what) {
        if( process.waitFor()==0 )
            return
        final output = process.text
        if( isDockerNameConflict(output) ) {
            log.warn "Docker container=$name already exists - adopting the existing container"
            startIfCreated(name, what)
            return
        }
        throw new IllegalStateException("Unable to launch ${what} - exitCode=${process.exitValue()}; output=${output}")
    }

    /**
     * Start the adopted container when the previous launch attempt created it but did not start it,
     * otherwise it would stay in the {@code created} state, reported as pending, until the job times out.
     * Containers in any other state are left as they are.
     */
    static protected void startIfCreated(String name, String what) {
        if( dockerContainerStatus(name)!='created' )
            return
        log.warn "Docker container=$name was created but never started - starting it"
        final process = new ProcessBuilder('docker', 'start', name).redirectErrorStream(true).start()
        if( process.waitFor()!=0 )
            throw new IllegalStateException("Unable to start ${what} - exitCode=${process.exitValue()}; output=${process.text}")
    }

    static protected String dockerContainerStatus(String name) {
        final process = new ProcessBuilder('docker', 'inspect', '--format', '{{.State.Status}}', name).redirectErrorStream(true).start()
        final output = process.text.trim()
        return process.waitFor()==0 ? output : null
    }

    static protected boolean isDockerNameConflict(String output) {
        return output?.contains('is already in use by container')
    }

    static void saveDockerAuth(Path workDir, String authJson) {
        // create the work directory
        Files.createDirectories(workDir)
        // save docker config for creds
        if( authJson ) {
            Path configFile = workDir.resolve('config.json')
            Files.write(configFile, JsonOutput.prettyPrint(authJson).bytes, CREATE, WRITE, TRUNCATE_EXISTING)
        }
    }

}
