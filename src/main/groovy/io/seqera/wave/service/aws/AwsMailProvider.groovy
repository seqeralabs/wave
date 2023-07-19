package io.seqera.wave.service.aws

import java.nio.ByteBuffer
import javax.mail.internet.MimeMessage

import com.amazonaws.AmazonClientException
import com.amazonaws.regions.InstanceMetadataRegionProvider
import com.amazonaws.services.simpleemail.model.RawMessage
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Primary
import io.micronaut.context.annotation.Requires
import io.seqera.wave.mail.MailProvider
import io.seqera.wave.mail.Mailer
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
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

    @Inject
    private AwsClientFactory clientFactory

    @PostConstruct
    private void init() {
        log.debug "+ Creating AWS SES mail provider"
    }

    void send(MimeMessage message, Mailer mailer) {
        final region = fetchRegion()
        final client = clientFactory.getEmailClient()
        // dump the message to a buffer
        final outputStream = new ByteArrayOutputStream()
        message.writeTo(outputStream)
        // send the email
        final rawMessage = new RawMessage(ByteBuffer.wrap(outputStream.toByteArray()))
        final result = client.sendRawEmail(new SendRawEmailRequest(rawMessage));
        log.debug "Mail message sent: ${result}"
    }

    /**
     * Retrieve the AWS region from the EC2 instance metadata.
     * See http://docs.aws.amazon.com/AWSEC2/latest/UserGuide/ec2-instance-metadata.html
     *
     * @return
     *      The AWS region of the current EC2 instance eg. {@code eu-west-1} or
     *      {@code null} if it's not an EC2 instance.
     */
    private String fetchRegion() {
        try {
            return System.getenv('AWS_REGION')
                    ?: System.getenv('AWS_DEFAULT_REGION')
                    ?: new InstanceMetadataRegionProvider().getRegion()
        }
        catch (AmazonClientException e) {
            log.error "Enable to retrieve AWS region - make sure either AWS_REGION or AWS_DEFAULT_REGION variable is defined or to run into a AWS Ec2 instance", e
            return null
        }
    }

}
