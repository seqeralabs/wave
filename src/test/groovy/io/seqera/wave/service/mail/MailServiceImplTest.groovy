package io.seqera.wave.service.mail

import spock.lang.Specification
import spock.lang.Unroll

import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.tower.User
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = 'mail')
class MailServiceImplTest extends Specification {

    @Inject MailServiceImpl service

    def 'should build build mail' () {
        given:
        def now = OffsetDateTime.now(ZoneId.of('UTC+2'))
        def recipient = 'foo@gmail.com'
        def result = BuildResult.completed('12345', 0, 'pull foo:latest', Instant.now())
        def request= new BuildRequest(
                'from foo', Path.of('.'), 'test', 'test', null, ContainerPlatform.DEFAULT, null,
                null, null, now)

        when:
        def mail = service.buildCompletionMail(request, result, recipient)
        then:
        mail.to == recipient
        mail.body.indexOf('<td>12345</td>') != -1 // request id
        mail.body.indexOf('<td>linux/amd64</td>') != -1 // platform
        mail.body.indexOf(
                '<td>'+DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("UTC+2")).format(Instant.now())+'</td>'
        ) != -1 // build time
    }

    @Unroll
    def 'should format duration' () {
        expect:
        service.formatDuration(DURATION) == EXPECTED
        where:
        DURATION                    | EXPECTED
        null                        | null
        Duration.ofSeconds(10)      | '0:10'
        Duration.ofSeconds(70)      | '1:10'
        Duration.ofMinutes(5)       | '5:00'
        Duration.ofMinutes(60)      | '60:00'
    }

}
