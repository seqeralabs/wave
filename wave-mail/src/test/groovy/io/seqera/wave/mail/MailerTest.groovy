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
import spock.lang.Timeout
import spock.lang.Unroll

import javax.mail.Message
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

import groovy.util.logging.Slf4j
import io.seqera.wave.mail.impl.MailProviderImpl
import org.subethamail.wiser.Wiser
import spock.util.environment.RestoreSystemProperties

@Slf4j
class MailerTest extends Specification {

    @Timeout(1)
    def 'should resolve name quickly' () {
        // if this test fails make sure the file `/etc/hosts`
        // contains something like the following
        // 127.0.0.1  localhost	 <computer name>.local
        // ::1		  localhost  <computer name>.local
        //
        //
        //  see more at this link https://thoeni.io/post/macos-sierra-java/
        expect:
        InetAddress.getLocalHost().getCanonicalHostName() != null
    }

    void 'should return config properties'() {
        when:
        MailerConfig config = new MailerConfig(smtp: [host: 'google.com', port: '808', user: 'foo', password: 'bar'])
        Mailer mailer = new Mailer( config: config  )
        Properties props = mailer.createProps()

        then:
        props.get('mail.smtp.user') == 'foo'
        props.get('mail.smtp.password') == 'bar'
        props.get('mail.smtp.host') == 'google.com'
        props.get('mail.smtp.port') == '808'
        !props.containsKey('mail.other')

    }

    @RestoreSystemProperties
    void 'should not configure global proxy setting' () {
        given:
        System.setProperty('http.proxyHost', 'foo.com')
        System.setProperty('http.proxyPort', '8000')

        MailerConfig config = new MailerConfig(smtp:[host: 'gmail.com', port: 25, user:'yo'])
        Mailer mailer = new Mailer(config: config)

        when:
        Properties props = mailer.createProps()

        then:
        props.'mail.smtp.host' == 'gmail.com'
        props.'mail.smtp.port' == '25'
        props.'mail.smtp.user' == 'yo'
        props.'mail.transport.protocol' == 'smtp'
        !props.containsKey('mail.smtp.proxy.host')
        !props.containsKey('mail.smtp.proxy.port')
    }


    def "sending mails using javamail"() {
        given:
        Integer PORT = 3025
        String USER = 'foo'
        String PASSWORD = 'secret'
        Wiser server = new Wiser(PORT)
        server.start()

        MailerConfig config = new MailerConfig(smtp: [host: 'localhost', port: PORT, user: USER, password: PASSWORD])
        MailProvider provider = new MailProviderImpl()
        Mailer mailer = new Mailer(config: config, provider: provider)

        String TO = "receiver@nextflow.io"
        String FROM = 'paolo@gmail.com'
        String SUBJECT = "Sending test"
        String CONTENT = "This content should be sent by the user."

        when:
        Map mail = [
                to: TO,
                from: FROM,
                subject: SUBJECT,
                body: CONTENT
        ]
        mailer.send(mail)

        then:
        server.messages.size() == 1
        Message message = server.messages.first().mimeMessage
        message.from == [new InternetAddress(FROM)]
        message.allRecipients.contains(new InternetAddress(TO))
        message.subject == SUBJECT
        message.content instanceof MimeMultipart
        (message.content as MimeMultipart).contentType.startsWith('multipart/related')

        cleanup:
        server?.stop()
    }

    def 'should send mail list' () {
        given:
        Integer PORT = 3025
        String USER = 'foo'
        String PASSWORD = 'secret'
        Wiser server = new Wiser(PORT)
        server.start()

        MailerConfig config = new MailerConfig(smtp: [host: 'localhost', port: PORT, user: USER, password: PASSWORD])
        Mailer mailer = new Mailer(config: config)

        def list = [
                Mail.of(to:'foo1@gmail.com', from:'bar1@gmail.com', subject: 'Hi 1', body: 'Hello 1'),
                Mail.of(from:'bar2@gmail.com', subject: 'Hi 2', body: 'Hello 2'),
                Mail.of(to:'foo3@gmail.com', from:'bar3@gmail.com', subject: 'Hi 3', body: 'Hello 3'),
        ]

        when:

        List<Mail> sentList = []
        List<Mail> failedList = []
        List<Exception> errorList = []
        def increment = { Mail m -> sentList.add(m) }
        def handleErr = { Mail m, Exception e -> failedList.add(m); errorList.add(e) }
        mailer.sendAll(list, [onSuccess:increment, onError: handleErr])

        then:
        sentList.size() == 2
        sentList[0].subject == 'Hi 1'
        sentList[1].subject == 'Hi 3'
        and:
        failedList.size() == 1
        failedList[0].subject == 'Hi 2'
        and:
        errorList.size() == 1
        errorList[0] instanceof NullPointerException
        and:
        server.messages.size() == 2
        server.messages[0].mimeMessage.subject == 'Hi 1'
        server.messages[1].mimeMessage.subject == 'Hi 3'

        cleanup:
        server?.stop()
    }

    def 'should not open transport connection' () {
        given:
        Mailer mailer = Spy(Mailer)
        def trasp = Mock(Transport)
        def actions = [onSuccess: null]
        def m1 = Mail.of(to:'foo')
        def m2 = Mail.of(to:'bar')

        when:
        mailer.sendAll([], [:])
        then:
        0 * mailer.getTransport0() >> null
        0 * mailer.createMessageAndSend0(_) >> null

        when:
        mailer.sendAll([m1,m2], actions)
        then:
        1 * mailer.getTransport0() >> trasp
        and:
        1 * trasp.connect(_, _, _, _) >> null
        and:
        1 * mailer.createMessageAndSend0(trasp, m1, actions) >> null
        1 * mailer.createMessageAndSend0(trasp, m2, actions) >> null
        and:
        1 * trasp.close()
    }

    def "sending mails using javamail (overrides 'to' address by config)"() {
        given:
        Integer PORT = 3025
        String USER = 'foo'
        String PASSWORD = 'secret'
        Wiser server = new Wiser(PORT)
        server.start()

        MailerConfig config = new MailerConfig(to: 'override@to.com', smtp: [host: 'localhost', port: PORT, user: USER, password: PASSWORD])
        MailProvider provider = new MailProviderImpl()
        Mailer mailer = new Mailer(config: config, provider: provider)

        String TO = "receiver@nextflow.io"
        String FROM = 'paolo@gmail.com'
        String SUBJECT = "Sending test"
        String CONTENT = "This content should be sent by the user."

        when:
        Map mail = [
                to: TO,
                from: FROM,
                subject: SUBJECT,
                body: CONTENT
        ]
        mailer.send(mail)

        then:
        server.messages.size() == 1
        Message message = server.messages.first().mimeMessage
        message.from == [new InternetAddress(FROM)]
        message.allRecipients.contains(new InternetAddress(config.to))
        message.subject == SUBJECT
        message.content instanceof MimeMultipart
        (message.content as MimeMultipart).contentType.startsWith('multipart/related')

        cleanup:
        server?.stop()
    }


    void 'should send with java' () {
        given:
        Mailer mailer = Spy(Mailer)
        MimeMessage MSG = Mock(MimeMessage)
        Mail mail = new Mail()
        and:
        MailProvider provider = Mock(MailProviderImpl)

        when:
        mailer.config = new MailerConfig(smtp: [host:'foo.com'])
        mailer.provider = provider
        mailer.send(mail)

        then:
        1 * mailer.createMimeMessage(mail) >> MSG
        1 * provider.send(MSG, mailer) >> null
    }



    void 'should create mime message' () {
        given:
        MimeMessage msg
        Mail mail

        when:
        mail = new Mail(from:'foo@gmail.com')
        msg = new Mailer(config: new MailerConfig(from:'fallback@hotmail.com')).createMimeMessage(mail)
        then:
        msg.from.size() == 1
        msg.from[0].toString() == 'foo@gmail.com'

        when:
        mail = new Mail()
        msg = new Mailer(config: new MailerConfig(from:'fallback@hotmail.com')).createMimeMessage(mail)
        then:
        msg.from.size() == 1
        msg.from[0].toString() == 'fallback@hotmail.com'

        when:
        mail = new Mail(from:'one@gmail.com, two@google.com')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.from.size() == 2
        msg.from[0].toString() == 'one@gmail.com'
        msg.from[1].toString() == 'two@google.com'

        when:
        mail = new Mail(to:'foo@gmail.com, bar@google.com')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.getRecipients(Message.RecipientType.TO).size()==2
        msg.getRecipients(Message.RecipientType.TO)[0].toString() == 'foo@gmail.com'
        msg.getRecipients(Message.RecipientType.TO)[1].toString() == 'bar@google.com'

        when:
        mail = new Mail(cc:'foo@gmail.com, bar@google.com')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.getRecipients(Message.RecipientType.CC).size()==2
        msg.getRecipients(Message.RecipientType.CC)[0].toString() == 'foo@gmail.com'
        msg.getRecipients(Message.RecipientType.CC)[1].toString() == 'bar@google.com'

        when:
        mail = new Mail(bcc:'one@gmail.com, two@google.com')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.getRecipients(Message.RecipientType.BCC).size()==2
        msg.getRecipients(Message.RecipientType.BCC)[0].toString() == 'one@gmail.com'
        msg.getRecipients(Message.RecipientType.BCC)[1].toString() == 'two@google.com'

        when:
        mail = new Mail(subject: 'this is a test', body: 'Ciao mondo')
        msg = new Mailer().createMimeMessage(mail)
        then:
        msg.subject == 'this is a test'
        msg.content instanceof MimeMultipart
        msg.content.count == 1
        msg.contentType.startsWith('text/plain')
        msg.content.getBodyPart(0).content.count == 1
        msg.content.getBodyPart(0).content.getBodyPart(0).content == 'Ciao mondo'
    }


    void 'should fetch config properties' () {
        given:
        Map SMTP = [host:'hola.com', user:'foo', password: 'bar', port: 234]
        Mailer mail

        when:
        mail = new Mailer(config: new MailerConfig(smtp: SMTP))
        then:
        mail.host == 'hola.com'
        mail.user == 'foo'
        mail.password == 'bar'
        mail.port == 234

    }


    void 'should capture send params' () {
        given:
        Mailer mailer = Spy(Mailer)

        when:
        mailer.send {
            to 'paolo@dot.com'
            from 'yo@dot.com'
            subject 'This is a test'
            body 'Hello there'
        }

        then:
        1 * mailer.send(_ as Mail) >> { Mail it ->
                        assert it.to == 'paolo@dot.com'
                        assert it.from == 'yo@dot.com'
                        assert it.subject == 'This is a test'
                        assert it.body == 'Hello there'
        }
    }


    void 'should strip html tags'  () {
        given:
        Mailer mailer = new Mailer()

        expect:
        mailer.stripHtml('Hello') == 'Hello'
        mailer.stripHtml('1 < 10 > 5') == '1 < 10 > 5'
        mailer.stripHtml('<h1>1 < 5</h1>') == '1 < 5'
        mailer.stripHtml('<h1>Big title</h1><p>Hello <b>world</b></p>') == 'Big title\nHello world'
    }


    void 'should capture multiline body' () {
        given:
        Mailer mailer = Spy(Mailer)
        String BODY = '''
            multiline
            mail
            content
            '''

        when:
        mailer.send {
            to 'you@dot.com'
            subject 'foo'
            BODY
        }

        then:
        1 * mailer.send(_ as Mail) >> { Mail it ->
            assert it.to == 'you@dot.com'
            assert it.subject == 'foo'
            assert it.body == BODY 
        }

    }

    void 'should guess html content' () {
        given:
        Mailer mailer = new Mailer()

        expect:
        !mailer.guessHtml('Hello')
        !mailer.guessHtml('1 < 10 > 5')
        mailer.guessHtml('<h1>1 < 5</h1>')
        mailer.guessHtml('1<br/>2')
        mailer.guessHtml('<h1>Big title</h1><p>Hello<br>world</p>')
    }

    @Unroll
    void 'should guess mime type' () {
        given:
        Mailer mailer = new Mailer()

        expect:
        mailer.guessMimeType(str) == type

        where:
        type            | str
        'text/plain'    | 'Hello'
        'text/plain'    | '1 < 10 > 5'
        'text/html'     | '<h1>1 < 5</h1>'
        'text/html'     | '1<br/>2'
        'text/html'     | '<h1>Big title</h1><p>Hello <b>world</b></p>'

    }

}
