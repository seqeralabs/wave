package io.seqera.wave.mail.impl

import javax.mail.internet.MimeMessage

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.mail.Mailer
import io.seqera.wave.mail.MailProvider
import jakarta.inject.Singleton

/**
 * Send a mime message via Java mail
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class MailProviderImpl implements MailProvider {

    /**
     * Send a email message by using the Java API
     *
     * @param message A {@link MimeMessage} object representing the email to send
     */
    @Override
    void send(MimeMessage message, Mailer mailer) {
        if( !message.getAllRecipients() )
            throw new IllegalArgumentException("Missing mail message recipient")

        final transport = mailer.getSession().getTransport()
        log.debug("Connecting to host=${mailer.host} port=${mailer.port} user=${mailer.user}")
        transport.connect(mailer.host, mailer.port as int, mailer.user, mailer.password)
        try {
            transport.sendMessage(message, message.getAllRecipients())
        }
        finally {
            transport.close()
        }
    }

}
