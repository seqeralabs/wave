/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.service.pairing.socket

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.data.future.AbstractFutureStore
import io.seqera.wave.service.data.future.FutureHash
import io.seqera.wave.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Singleton

/**
 * Model an distribute store for completable future that
 * used to collect inbound messages
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class PairingInboundStore extends AbstractFutureStore<PairingMessage> {

    @Value('${wave.pairing.channel.timeout:5s}')
    Duration timeout

    PairingInboundStore(FutureHash<String> publisher) {
        super(publisher, new MoshiEncodeStrategy<PairingMessage>() {})
    }

    @Override
    String prefix() {
        return "pairing-inbound-queue/v1:"
    }

    String name() { "inbound-queue" }

}
