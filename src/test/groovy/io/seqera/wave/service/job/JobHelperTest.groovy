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

import spock.lang.Specification

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class JobHelperTest extends Specification {

    static final String CONFLICT = 'docker: Error response from daemon: Conflict. The container name "/bd-123" is already in use by container "83af2520a3f0". You have to remove (or rename) that container to be able to reuse that name.'

    static Process run(String output, int exitCode) {
        new ProcessBuilder('sh', '-c', "printf '%s' \"\$0\"; exit $exitCode", output)
                .redirectErrorStream(true)
                .start()
    }

    def 'should complete a successful docker launch'() {
        when:
        JobHelper.awaitDockerLaunch(run('83af2520a3f0', 0), 'bd-123', 'build container')

        then:
        noExceptionThrown()
    }

    def 'should adopt the existing container when a launch is redelivered'() {
        when:
        JobHelper.awaitDockerLaunch(run(CONFLICT, 125), 'bd-123', 'build container')

        then:
        noExceptionThrown()
    }

    def 'should propagate a docker launch failure other than a name conflict'() {
        when:
        JobHelper.awaitDockerLaunch(run('Unable to find image', 125), 'bd-123', 'build container')

        then:
        def e = thrown(IllegalStateException)
        e.message == 'Unable to launch build container - exitCode=125; output=Unable to find image'
    }

    def 'should detect a docker name conflict'() {
        expect:
        JobHelper.isDockerNameConflict(OUTPUT) == EXPECTED

        where:
        OUTPUT                  | EXPECTED
        null                    | false
        ''                      | false
        'Unable to find image'  | false
        CONFLICT                | true
    }
}
