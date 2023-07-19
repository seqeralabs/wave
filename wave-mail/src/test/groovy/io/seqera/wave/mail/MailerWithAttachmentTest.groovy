/*
 * Copyright (c) 2019-2020, Seqera Labs.
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 * This Source Code Form is "Incompatible With Secondary Licenses", as
 * defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.mail

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.Path
import javax.mail.Message
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMultipart

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import io.seqera.wave.mail.impl.MailProviderImpl
import org.subethamail.wiser.Wiser
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class MailerWithAttachmentTest extends Specification {


    void "should send email with attachment"() {
        given:
        Integer PORT = 3025
        String USER = 'foo'
        String PASSWORD = 'secret'
        Wiser server = new Wiser(PORT)
        server.start()

        MailerConfig config = new MailerConfig(smtp:[host: '127.0.0.1', port: PORT, user: USER, password: PASSWORD])
        MailProvider provider = new MailProviderImpl()
        Mailer mailer = new Mailer(config: config, provider: provider)

        String TO = "receiver@gmail.com"
        String FROM = 'paolo@nextflow.io'
        String SUBJECT = "Sending test"
        String CONTENT = "This content should be sent by the user."
        Path ATTACH = Files.createTempFile('test', null)
        ATTACH.toFile().text = 'This is the file attachment content'

        when:
        Map mail = [
                from: FROM,
                to: TO,
                subject: SUBJECT,
                body: CONTENT,
                attach: ATTACH
        ]
        mailer.send(mail)

        then:
        server.messages.size() == 1
        Message message = server.messages.first().mimeMessage
        message.from == [new InternetAddress(FROM)]
        message.allRecipients.contains(new InternetAddress(TO))
        message.subject == SUBJECT
        (message.content as MimeMultipart).count == 2

        cleanup:
        if( ATTACH ) Files.delete(ATTACH)
        server?.stop()
    }
}
