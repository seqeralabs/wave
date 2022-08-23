package io.seqera.wave.service.mail

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.mail.Mail
import io.seqera.wave.mail.MailAttachment
import io.seqera.wave.mail.MailHelper
import io.seqera.wave.mail.MailerConfig
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(env = 'mail')
@Singleton
@CompileStatic
class MailServiceImpl implements MailService {

    @Inject
    private MailSpooler spooler

    @Inject
    private MailerConfig config

    @Value('${wave.server.url}')
    private String serverUrl

    @Override
    void sendCompletionMail(BuildRequest request, BuildResult build) {
        // send to user email address or fallback to the system `mail.from` address
        final user = request.user
        final recipient = user ? user.email : config.from
        if( recipient ) {
            final arg = build ?: BuildResult.UNKNOWN
            final mail = buildCompletionMail(arg, request.targetImage, request.dockerFile, recipient)
            spooler.sendMail(mail)
        }
        else {
            log.debug "Missing email recipient from build request id=$build.id - user=$user"
        }
    }

    Mail buildCompletionMail(BuildResult build, String targetImage, String dockerfile, String recipient) {
        // create template binding
        final binding = new HashMap(5)
        final status = build.exitStatus==0 ? 'DONE': 'FAILED'
        binding.build_id = build.id
        binding.build_success = build.exitStatus==0
        binding.build_exit_status = build.exitStatus
        binding.build_time = formatTimestamp(build.startTime) ?: '-'
        binding.build_duration = formatDuration(build.duration) ?: '-'
        binding.build_image = targetImage
        binding.build_dockerfile = dockerfile ?: '-'
        binding.put('build_logs', build.logs)
        binding.put('server_url', serverUrl)
        // build the main object
        Mail mail = new Mail()
        mail.to(recipient)
        mail.subject("Wave container build completion - ${status}")
        mail.body(MailHelper.getTemplateFile('/io/seqera/wave/build-notification.html', binding))
        mail.attach(MailAttachment.resource('/io/seqera/wave/seqera-logo.png', contentId: '<seqera-logo>', disposition: 'inline'))
        return mail
    }

    protected String formatDuration(Duration duration) {
        if( duration==null )
            return null
        final time = duration.toMillis()
        int minutes = time / (60 * 1_000) as int
        int seconds = (time / 1_000 as int) % 60
        return String.format("%d:%02d", minutes, seconds);
    }

    protected String formatTimestamp(Instant instant) {
        if( instant==null )
            return null
        return instant.toString()
    }
}
