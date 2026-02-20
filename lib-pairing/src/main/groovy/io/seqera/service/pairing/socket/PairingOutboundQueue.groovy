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
import java.util.concurrent.ExecutorService
import jakarta.annotation.PreDestroy

import groovy.transform.CompileStatic
import io.micronaut.scheduling.TaskExecutors
import io.seqera.data.queue.MessageQueue
import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.service.pairing.PairingConfig
import io.seqera.service.pairing.socket.msg.PairingMessage
import jakarta.inject.Named
import jakarta.inject.Singleton

/**
 * Implement a distributed queue for Wave pairing messages
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Singleton
@CompileStatic
class PairingOutboundQueue extends BaseMessageQueue<PairingMessage> {

    final private PairingConfig config

    PairingOutboundQueue(
            MessageQueue<String> broker,
            PairingConfig config,
            @Named(TaskExecutors.BLOCKING) ExecutorService ioExecutor
    ) {
        super(broker, ioExecutor)
        this.config = config
    }

    @Override
    protected String prefix() {
        return 'pairing-outbound-queue/v1:'
    }

    @Override
    protected String name() { "outbound-queue" }

    @Override
    protected Duration pollInterval() { return config.channelAwaitTimeout }

    @PreDestroy
    void close() {
        super.close()
    }

    @Override
    protected MoshiEncodeStrategy<PairingMessage> createEncodingStrategy() {
        return PairingMessageEncodeStrategy.create()
    }
}
