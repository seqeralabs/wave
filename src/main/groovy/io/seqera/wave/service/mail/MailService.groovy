package io.seqera.wave.service.mail

import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.tower.User

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface MailService {

    void sendCompletionMail(BuildResult build, User user)

}
