package io.seqera.wave.mail

import javax.mail.internet.MimeMessage

import io.seqera.wave.mail.Mailer

/**
 * Define a generic interface to send an email modelled as a Mime message object
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
interface MailProvider {

    void send(MimeMessage message, Mailer mailer)

}
