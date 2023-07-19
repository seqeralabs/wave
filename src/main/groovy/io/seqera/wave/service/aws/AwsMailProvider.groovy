package io.seqera.wave.service.aws

import java.nio.ByteBuffer
import javax.mail.internet.MimeMessage

import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClientBuilder
import com.amazonaws.services.simpleemail.model.RawMessage
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.seqera.wave.mail.MailProvider
import io.seqera.wave.mail.Mailer
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
/**
 * Send a mime message via AWS SES raw API
 *
 * https://docs.aws.amazon.com/ses/latest/dg/send-email-raw.html
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Primary
@Requires(env = 'aws-ses')
@Singleton
@CompileStatic
@Slf4j
class AwsMailProvider implements MailProvider {

    @PostConstruct
    private void init() {
        log.debug "+ Creating AWS SES mail provider"
    }

    void send(MimeMessage message, Mailer mailer) {
        //get mail client
        final client = AmazonSimpleEmailServiceClientBuilder
                .standard()
                .build()
        // dump the message to a buffer
        final outputStream = new ByteArrayOutputStream()
        message.writeTo(outputStream)
        // send the email
        final rawMessage = new RawMessage(ByteBuffer.wrap(outputStream.toByteArray()))
        final result = client.sendRawEmail(new SendRawEmailRequest(rawMessage));
        log.debug "Mail message sent: ${result}"
    }
}
