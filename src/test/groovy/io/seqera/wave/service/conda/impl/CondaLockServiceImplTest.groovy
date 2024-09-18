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

package io.seqera.wave.service.conda.impl

import spock.lang.Specification

import java.nio.charset.StandardCharsets

import io.seqera.wave.service.persistence.PersistenceService
import io.seqera.wave.service.persistence.WaveCondaLockRecord

/**
 * Tests for {@link CondaLockServiceImpl}
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
class CondaLockServiceImplTest extends Specification {

    def "should store conda lockfile" (){
        given:
        def persistenceService = Mock(PersistenceService)
        def service = new CondaLockServiceImpl(persistenceService: persistenceService)

        and:
        def logs = """
                logs....
                #10 12.24 conda_lock_start
                #10 12.24 # This file may be used to create an environment using:
                #10 12.24 # \$ conda create --name <env> --file <this file>
                #10 12.24 # platform: linux-aarch64
                #10 12.24 @EXPLICIT
                #10 12.25 conda_lock_end
                logs....""".stripIndent()

        when:
        service.storeCondaLock("someId", logs)

        then:
        1 * persistenceService.saveCondaLock(_ as WaveCondaLockRecord)
    }

    def "should not store conda lockfile when logs does not exist" (){
        given:
        def persistenceService = Mock(PersistenceService)
        def service = new CondaLockServiceImpl(persistenceService: persistenceService)

        when:
        service.storeCondaLock("someId", null)

        then:
        0 * persistenceService.saveCondaLock(_)
    }

    def "should fetch conda lockfile" () {
        given:
        def persistenceService = Mock(PersistenceService)
        def service = new CondaLockServiceImpl(persistenceService: persistenceService)
        def record = new WaveCondaLockRecord("someId", "conda lock content".getBytes(StandardCharsets.UTF_8))
        persistenceService.loadCondaLock("someId") >> record

        when:
        def condaLock = service.fetchCondaLock("someId")

        then:
        condaLock.inputStream.text == "conda lock content"
    }

    def "should return null when buildId is null" () {
        given:
        def persistenceService = Mock(PersistenceService)
        def service = new CondaLockServiceImpl(persistenceService: persistenceService)

        when:
        def condaLock = service.fetchCondaLock(null)

        then:
        condaLock == null
    }

    def 'should extract conda lockfile' () {
        def logs = """
                #9 12.23 logs....
                #10 12.24 conda_lock_start
                #10 12.24 # This file may be used to create an environment using:
                #10 12.24 # \$ conda create --name <env> --file <this file>
                #10 12.24 # platform: linux-aarch64
                #10 12.24 @EXPLICIT
                #10 12.25 conda_lock_end
                #11 12.26 logs....""".stripIndent()
        def service = new CondaLockServiceImpl()

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
