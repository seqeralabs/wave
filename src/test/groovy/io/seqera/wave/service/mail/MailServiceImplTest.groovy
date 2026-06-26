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

package io.seqera.wave.service.mail

import spock.lang.Specification

import java.time.Instant

import io.micronaut.test.annotation.MockBean
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.mail.impl.MailServiceImpl
import io.seqera.wave.service.mail.impl.MailSpoolerImpl
import io.seqera.wave.tower.PlatformId
import io.seqera.wave.tower.User
import io.seqera.wave.tower.WaveBuildNotification
import jakarta.inject.Inject

@MicronautTest(environments = 'mail')
class MailServiceImplTest extends Specification {

    @Inject MailServiceImpl service

    @Inject
    MailSpooler spooler

    @MockBean(MailSpoolerImpl)
    MailSpooler mockSpooler() {
        Mock(MailSpooler)
    }

    def 'should create build mail' () {
        given:
        def recipient = 'foo@gmail.com'
        def result = BuildResult.completed('12345', 0, 'pull foo:latest', Instant.now(), 'abc')
        def request= Mock(BuildRequest)

        when:
        def mail = service.buildCompletionMail(request, result, recipient)
        then:
        1* request.getContainerFile() >> 'from foo';
        1* request.getTargetImage() >> 'seqera.io/wave/build:xyz'
        1* request.getPlatform() >> ContainerPlatform.DEFAULT
        1* request.getCondaFile() >> null
        and:
        mail.to == recipient
        mail.body.contains('from foo')
        mail.body.contains('seqera&#8203;.io/wave/build:xyz')
        and:
        !mail.body.contains('Conda file')

        // check it adds the Conda file content
        when:
        mail = service.buildCompletionMail(request, result, recipient)
        then:
        1* request.getTargetImage() >> 'wave/build:xyz'
        1* request.getPlatform() >> ContainerPlatform.DEFAULT
        1* request.getCondaFile() >> 'bioconda::foo'
        and:
        mail.to == recipient
        mail.body.contains('Conda file')
        mail.body.contains('bioconda::foo')

    }

    def 'should replace dot with non breaking name' () {
        expect:
        MailServiceImpl.preventLinkFormatting(NAME) == EXPECTED

        where:
        NAME                         | EXPECTED
        null                         | null
        'foo'                        | 'foo'
        'www.host.com/this/that'     | 'www&#8203;.host&#8203;.com/this/that'
    }

    def 'sendCompletionEmail respects waveBuildNotification preference'() {
        given:
        def user = new User(id: 1L, userName: 'testuser', email: 'test@example.com',
                waveBuildNotification: pref)
        def identity = new PlatformId(user, null, null, null, null)
        def request = Stub(BuildRequest) {
            getIdentity()      >> identity
            getIp()            >> '1.2.3.4'
            getOffsetId()      >> 'UTC'
            getTargetImage()   >> 'wave/build:test'
            getFormat()        >> null
            getCompression()   >> null
            getPlatform()      >> ContainerPlatform.DEFAULT
            getBuildTemplate() >> null
            getContainerFile() >> 'FROM ubuntu'
            getCondaFile()     >> null
        }
        def result = BuildResult.completed('bd-test_0', exitCode, 'logs', Instant.now(),
                exitCode == 0 ? 'sha256:abc' : null)

        when:
        service.sendCompletionEmail(request, result)

        then:
        expectedCalls * spooler.sendMail(_)

        where:
        pref                             | exitCode | expectedCalls
        null                             | 0        | 1
        null                             | 1        | 1
        WaveBuildNotification.ALWAYS_ON  | 0        | 1
        WaveBuildNotification.ALWAYS_ON  | 1        | 1
        WaveBuildNotification.ON_ERROR   | 0        | 0
        WaveBuildNotification.ON_ERROR   | 1        | 1
        WaveBuildNotification.ALWAYS_OFF | 0        | 0
        WaveBuildNotification.ALWAYS_OFF | 1        | 0
    }
}
