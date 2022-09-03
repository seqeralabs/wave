package io.seqera.wave.service.mail

import spock.lang.Specification
import spock.lang.Unroll

import java.time.Duration
import java.time.Instant

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
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
        def recipient = 'foo@gmail.com'
        def result = new BuildResult('12345', 0, 'pull foo:latest', Instant.now())
        def request= Mock(BuildRequest) {
            getDockerFile() >> 'from foo';
            getTargetImage() >> 'wave/build:xyz'
            getPlatform() >> ContainerPlatform.DEFAULT
        }
        
        when:
        def mail = service.buildCompletionMail(request, result, recipient)
        then:
        mail.to == recipient
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
