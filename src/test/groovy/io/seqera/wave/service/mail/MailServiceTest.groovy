package io.seqera.wave.service.mail

import spock.lang.Specification

import java.time.Instant

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.service.builder.BuildResult
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest(environments = 'mail')
class MailServiceTest extends Specification {

    @Inject MailServiceImpl service

    def 'should build build mail' () {
        given:
        def recipient = 'foo@gmail.com'
        def result = new BuildResult('12345', 0, 'pull foo:latest', Instant.now())
        when:
        def mail = service.buildCompletionMail(result, recipient)
        then:
        mail.to == recipient
    }

}
