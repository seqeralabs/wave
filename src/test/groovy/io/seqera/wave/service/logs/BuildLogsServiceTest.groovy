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

package io.seqera.wave.service.logs

import spock.lang.Specification
import spock.lang.Unroll

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class BuildLogsServiceTest extends Specification {

    @Unroll
    def 'should make log key name' () {
        expect:
        new BuildLogServiceImpl(prefix: PREFIX).logKey(BUILD) == EXPECTED

        where:
        PREFIX          | BUILD         | EXPECTED
        null            | null          | null
        null            | '123'         | '123.log'
        'foo'           | '123'         | 'foo/123.log'
        '/foo/bar/'     | '123'         | 'foo/bar/123.log'
    }

    def 'should remove conda lockfile from logs' () {
        def logs = """
                #9 12.23 logs....
                #10 12.24 >>>>>>> CONDA_LOCK_START
                #10 12.24 # This file may be used to create an environment using:
                #10 12.24 # \$ conda create --name <env> --file <this file>
                #10 12.24 # platform: linux-aarch64
                #10 12.24 @EXPLICIT
                #10 12.25 <<<<<<< CONDA_LOCK_END
                #11 12.26 logs....""".stripIndent()
        def service = new BuildLogServiceImpl()

        when:
        def result = service.removeCondaLockFile(logs)
        then:
        result == """
             #9 12.23 logs....
             #11 12.26 logs....""".stripIndent()
    }

    @Unroll
    def 'should make conda lock key name' () {
        expect:
        new BuildLogServiceImpl(condaLockPrefix: PREFIX).condaLockKey(BUILD) == EXPECTED

        where:
        PREFIX          | BUILD         | EXPECTED
        null            | null          | null
        null            | '123'         | '123.lock'
        'foo'           | '123'         | 'foo/123.lock'
        '/foo/bar/'     | '123'         | 'foo/bar/123.lock'
    }

    def 'should extract conda lockfile' () {
        def logs = """
                #9 12.23 logs....
                #10 12.24 >>>>>>> CONDA_LOCK_START
                #10 12.24 # This file may be used to create an environment using:
                #10 12.24 # \$ conda create --name <env> --file <this file>
                #10 12.24 # platform: linux-aarch64
                #10 12.24 @EXPLICIT
                #10 12.25 <<<<<<< CONDA_LOCK_END
                #11 12.26 logs....""".stripIndent()
        def service = new BuildLogServiceImpl()

        when:
        def result = service.extractCondaLockFile(logs)

        then:
        result == """
             # This file may be used to create an environment using:
             # \$ conda create --name <env> --file <this file>
             # platform: linux-aarch64
             @EXPLICIT
             """.stripIndent()
    }

}
