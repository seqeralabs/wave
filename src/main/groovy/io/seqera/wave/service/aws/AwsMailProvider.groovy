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
import io.seqera.mail.MailProvider
import io.seqera.mail.Mailer
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

    @Override
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
