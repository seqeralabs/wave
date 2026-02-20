/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.service.pairing.socket

import java.time.Duration

import groovy.transform.CompileStatic
import io.seqera.data.store.future.AbstractFutureStore
import io.seqera.data.store.future.FutureHash
import io.seqera.service.pairing.PairingConfig
import io.seqera.service.pairing.socket.msg.PairingMessage
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

    private final PairingConfig config

    PairingInboundStore(FutureHash<String> publisher, PairingConfig config) {
        super(publisher, PairingMessageEncodeStrategy.create())
        this.config = config
    }

    @Override
    String prefix() {
        return "pairing-inbound-queue/v1:"
    }

    String name() { "inbound-queue" }

    @Override
    Duration getTimeout() {
        return config.channelTimeout
    }

    @Override
    Duration getPollInterval() {
        return config.channelAwaitTimeout
    }

}
