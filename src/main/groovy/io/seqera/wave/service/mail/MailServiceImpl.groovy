package io.seqera.wave.service.mail

import java.time.Duration
import javax.annotation.Nullable

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.seqera.wave.mail.Mail
import io.seqera.wave.mail.MailAttachment
import io.seqera.wave.mail.MailHelper
import io.seqera.wave.mail.MailerConfig
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.tower.User
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
    void sendCompletionMail(BuildResult build, @Nullable User user) {
        // send to user email address or fallback to the system `mail.from` address
        final recipient = user ? user.email : config.from
        if( recipient ) {
            final arg = build ?: BuildResult.ERROR
            final mail = buildCompletionMail(arg, recipient)
            spooler.sendMail(mail)
        }
        else {
            log.debug "Missing email recipient from build request id=$build.id - user=$user"
        }
    }

    Mail buildCompletionMail(BuildResult build, String recipient) {
        // create template binding
        final binding = new HashMap(5)
        final status = build.exitStatus==0 ? 'DONE': 'FAILED'
        binding.build_id = build.id
        binding.build_success = build.exitStatus==0
        binding.build_exit_status = build.exitStatus
        binding.build_time = build.startTime?.toString() ?: '-'
        binding.build_duration = build.duration ? formatDuration(build.duration) : '-'
        binding.put('server_url', serverUrl)
        // strip ansi escape codes
        final logs = build.getLogs()?.replaceAll("\u001B\\[[;\\d]*m", "")
        binding.put('logs', logs)
        // build the main object
        Mail mail = new Mail()
        mail.to(recipient)
        mail.subject("Wave container build completion - ${status}")
        mail.body(MailHelper.getTemplateFile('/io/seqera/wave/build-notification.html', binding))
        mail.attach(MailAttachment.resource('/io/seqera/wave/seqera-logo.png', contentId: '<seqera-logo>', disposition: 'inline'))
        return mail
    }

    protected String formatDuration(Duration duration) {
        final time = duration.toMillis()
        int minutes = time / (60 * 1_000) as int
        int seconds = (time / 1_000 as int) % 60
        return String.format("%d:%02d", minutes, seconds);
    }
}
