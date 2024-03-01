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

import javax.mail.internet.MimeMessage

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.mail.MailProvider
import io.seqera.mail.Mailer
import jakarta.annotation.PostConstruct
import jakarta.inject.Singleton
import software.amazon.awssdk.core.SdkBytes
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.ses.SesClient
import software.amazon.awssdk.services.ses.model.RawMessage
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest
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

    @Value("wave.mail.ses.region")
    String region

    @PostConstruct
    private void init() {
        log.debug "+ Creating AWS SES mail provider"
    }

    @Override
    void send(MimeMessage message, Mailer mailer) {
        //get mail client
        final client = SesClient.builder()
                .region(Region.of(region))
                .build();
        // dump the message to a buffer
        final outputStream = new ByteArrayOutputStream()
        message.writeTo(outputStream)
        final sdkBytes = SdkBytes.fromByteArray(outputStream.toByteArray());
        // send the email
        final rawMessage = RawMessage.builder()
                .data(sdkBytes)
                .build()
        final email = SendRawEmailRequest.builder()
                .rawMessage(rawMessage)
                .build()
        final result = client.sendRawEmail(email);
        log.debug "Mail message sent: ${result}"
    }
}
