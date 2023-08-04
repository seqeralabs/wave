package io.seqera.wave.mail.impl

import spock.lang.Specification

import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.mail.Mailer
import jakarta.inject.Inject

@MicronautTest
class MailProviderImplTest extends Specification {

    @Inject
    MailProviderImpl mailProvider

    def "should throw IllegalArgumentException"() {

        given:
        MimeMessage mimeMessage = Mock(MimeMessage)
        Mailer mailer = Mock(Mailer)

        when:
        mailProvider.send(mimeMessage, mailer)

        then:
        thrown(IllegalArgumentException)
    }
}
