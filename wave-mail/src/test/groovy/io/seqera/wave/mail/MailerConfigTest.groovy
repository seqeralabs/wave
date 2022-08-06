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

import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@MicronautTest
class MailerConfigTest extends Specification {

    @Inject
    MailerConfig mailerConfig

    void 'should create smtp config' () {
        expect:
        mailerConfig.from == 'me@google.com'
        mailerConfig.smtp.host == 'localhost'
        mailerConfig.smtp.user == 'mr-bean'
        mailerConfig.smtp.password == 'super-secret'
        mailerConfig.smtp.port == 3025
        mailerConfig.smtp.auth == true
        mailerConfig.smtp.'starttls.enable' == false
        mailerConfig.smtp.'starttls.required' == false
    }


    void 'should get mail properties object' () {
        when:
        def props = mailerConfig.mailProperties

        then:
        props.'mail.smtp.host' == 'localhost'
        props.'mail.smtp.user' == 'mr-bean'
        props.'mail.smtp.password' == 'super-secret'
        props.'mail.smtp.port' == '3025'
        props.'mail.smtp.auth' == 'true'
        props.'mail.smtp.starttls.enable' == 'false'
        props.'mail.smtp.starttls.required' == 'false'
        props.'mail.smtp.proxy.host' == 'proxy.com'
        props.'mail.smtp.proxy.port' == '5566'
        props.'mail.transport.protocol' == 'smtp'
    }

}


