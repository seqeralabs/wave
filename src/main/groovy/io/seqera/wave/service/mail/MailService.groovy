package io.seqera.wave.service.mail

import io.seqera.wave.model.BuildRequest
import io.seqera.wave.model.BuildResult
/**
 * Implements mail notification service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface MailService {

    void sendCompletionEmail(BuildRequest request, BuildResult result)

}
