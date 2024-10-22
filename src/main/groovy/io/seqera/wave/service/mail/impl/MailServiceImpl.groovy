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

package io.seqera.wave.service.mail.impl


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.context.annotation.Value
import io.micronaut.runtime.event.annotation.EventListener
import io.seqera.mail.Mail
import io.seqera.mail.MailAttachment
import io.seqera.mail.MailHelper
import io.seqera.mail.MailerConfig
import io.seqera.wave.service.builder.BuildEvent
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.mail.MailService
import io.seqera.wave.service.mail.MailSpooler
import jakarta.inject.Inject
import jakarta.inject.Singleton
import static io.seqera.wave.util.DataTimeUtils.formatDuration
import static io.seqera.wave.util.DataTimeUtils.formatTimestamp

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

    @EventListener
    void onBuildEvent(BuildEvent event) {
        try {
            sendCompletionEmail(event.request, event.result)
        }
        catch (Exception e) {
            log.warn "Unable to send completion notification - reason: ${e.message ?: e}"
        }
    }

    @Override
    void sendCompletionEmail(BuildRequest request, BuildResult build) {
        // send to user email address or fallback to the system `mail.from` address
        final user = request.identity.user
        final recipient = user ? user.email : config.from
        if( recipient ) {
            final result = build ?: BuildResult.unknown()
            final mail = buildCompletionMail(request, result, recipient)
            spooler.sendMail(mail)
        }
        else {
            log.debug "Missing email recipient from build id=$build.buildId - user=$user"
        }
    }

    Mail buildCompletionMail(BuildRequest req, BuildResult result, String recipient) {
        // create template binding
        final binding = new HashMap(20)
        final status = result.succeeded() ? 'DONE': 'FAILED'
        binding.build_id = result.buildId
        binding.build_user =  "${req.identity?.user ? req.identity.user.userName : '-'} (${req.ip})"
        binding.build_success = result.succeeded()
        binding.build_exit_status = result.exitStatus
        binding.build_time = formatTimestamp(result.startTime, req.offsetId) ?: '-'
        binding.build_duration = formatDuration(result.duration) ?: '-'
        binding.build_image = preventLinkFormatting(req.targetImage)
        binding.build_format = req.format?.render() ?: 'Docker'
        binding.build_platform = req.platform
        binding.build_containerfile = req.containerFile ?: '-'
        binding.build_condafile = req.condaFile
        binding.build_digest = result.digest ?: '-'
        binding.build_url = "$serverUrl/view/builds/${result.buildId}"
        binding.scan_url = req.scanId && result.succeeded() ? "$serverUrl/view/scans/${req.scanId}" : null
        binding.scan_id = req.scanId
        binding.put('server_url', serverUrl)
        // result the main object
        Mail mail = new Mail()
        mail.to(recipient)
        mail.subject("Wave container build completion - ${status}")
        mail.body(MailHelper.getTemplateFile('/io/seqera/wave/build-notification.html', binding))
        mail.attach(MailAttachment.resource('/io/seqera/wave/assets/wave-logo.png', contentId: '<wave-logo>', disposition: 'inline'))
        mail.attach(MailAttachment.resource('/io/seqera/wave/assets/seqera-logo.png', contentId: '<seqera-logo>', disposition: 'inline'))
        return mail
    }

    /**
     * Replaces dot characters in the image name with zero-with blank entity followed by a dot
     * to prevent the mail client to render the container image name as a uri.
     *
     * @param name
     *      The container image name as a string
     * @return
     *      The image string in which dot chars are replaced with {@code &#8203;.}
     */
    static protected String preventLinkFormatting(String name) {
        name ? name.replaceAll(/\./,'&#8203;.') : null
    }
}
