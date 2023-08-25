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

package io.seqera.wave.service.mail.impl

import java.time.Instant
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import javax.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.event.annotation.EventListener
import io.micronaut.runtime.server.event.ServerShutdownEvent
import io.micronaut.runtime.server.event.ServerStartupEvent
import io.seqera.mail.Mail
import io.seqera.mail.MailProvider
import io.seqera.mail.Mailer
import io.seqera.mail.MailerConfig
import io.seqera.wave.service.mail.MailSpooler
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Simple mail sender service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(env = 'mail')
@Singleton
@CompileStatic
class MailSpoolerImpl implements MailSpooler {

    @Inject
    MailerConfig config

    @Inject
    MailProvider provider

    BlockingQueue<Mail> pendingMails

    private volatile boolean terminated

    private volatile int errorCount

    private volatile int sentCount

    private String errorMessage

    private Instant errorTimestamp

    private Mailer mailer

    private long awaitMillis

    private Thread thread

    private Map<UUID,Integer> mailErrors = new HashMap<>()


    @PostConstruct
    void init() {
        pendingMails = createMailQueue()
    }

    BlockingQueue<Mail> createMailQueue() {
        new LinkedBlockingQueue<Mail>()
    }

    @Override
    void sendMail(Mail mail) {
        assert mail, 'Mail object cannot be null'
        // create random UUID
        mail.id = UUID.randomUUID()
        // set from if missing
        if( !mail.from )
            mail.from(config.from)
        // add to pending queue
        pendingMails.add(mail)
    }

    protected sendLoop(dummy) {
        while(!terminated) {
            if( awaitMillis ) {
                sleep(awaitMillis)
                awaitMillis=0
            }

            takeAndSendMail0()
        }
    }

    protected void takeAndSendMail0() {
        Mail mail = null
        try {
            mail = pendingMails.take()
            mailer.send(mail)
            log.debug "Mail sent to=${mail.to}; from=$mail.from; subject=${mail.subject}"
            sentCount +=1
            errorCount =0
        }
        catch (InterruptedException e) {
            log.warn("Mail service got interrupted")
        }
        catch (Throwable e) {
            errorCount = retryOrDiscard(mail,e)
            errorMessage = e.message
            errorTimestamp = Instant.now()
            awaitMillis = Math.min(250 * Math.pow(3, errorCount) as long, 20_000)
            log.error("Unexpected error sending mail (await $awaitMillis ms) - ${e.message}")
        }
    }

    protected int retryOrDiscard(Mail mail, Throwable e) {
        if( !mail ) {
            return errorCount +=1
        }

        // certain error cannot be record - just discard it
        if( e.message?.contains('Invalid Address') || e.message?.contains('address is not verified')) {
            return 0
        }

        final result = mailErrors.getOrDefault(mail.id, 0)+1
        if( errorCount<=3 ) {
            // re-try sending
            mailErrors.put(mail.id, errorCount)
            pendingMails.offer(mail)
            return result
        }

        // discard it
        mailErrors.remove(mail.id)
        log.debug "Permanent error sending email to recipient: $mail.to - Discarding message"
        return 0
    }

    @EventListener
    void start(ServerStartupEvent event) {
        log.info "+ Mail service started [${this.getClass().getSimpleName()}]; provider=${provider.getClass().getSimpleName()}"
        mailer = new Mailer().setConfig(config).setProvider(provider)
        thread = Thread.startDaemon('Mailer thread',this.&sendLoop)
    }

    @EventListener
    void stop(ServerShutdownEvent event) {
        log.info "+ Mail service stopped"
        terminated = true
        thread.interrupt()
    }

}
