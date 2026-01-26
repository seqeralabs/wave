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

import java.util.concurrent.ExecutorService

import io.seqera.lang.type.TypeHelper
import io.seqera.serde.encode.StringEncodingStrategy
import io.seqera.data.queue.AbstractMessageQueue
import io.seqera.data.queue.MessageQueue
import io.seqera.serde.moshi.MoshiEncodeStrategy

/**
 * Base abstract class for implementing message queues in the pairing library.
 *
 * <p>This class extends {@link AbstractMessageQueue} and provides a foundation for
 * creating type-safe message queues with automatic JSON serialization/deserialization
 * using the Moshi library. It handles the encoding strategy configuration and
 * provides a consistent interface for message queue implementations.</p>
 *
 * <p>Concrete implementations should extend this class and implement the required
 * abstract methods from the parent class to define specific queue behavior.</p>
 *
 * @param <M> the type of messages that this queue will handle
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class BaseMessageQueue<M> extends AbstractMessageQueue<M> {

    /**
     * Constructs a new BaseMessageQueue with the specified broker and executor.
     *
     * @param broker the underlying string-based message queue broker that handles
     *               the actual message transport and storage
     * @param ioExecutor the executor service used for asynchronous I/O operations
     *                   and message processing tasks
     */
    BaseMessageQueue(MessageQueue<String> broker, ExecutorService ioExecutor) {
        super(broker, ioExecutor)
    }

    /**
     * Creates an instance of the required {@link StringEncodingStrategy} to serialize
     * and deserialize message events to/from JSON format.
     *
     * <p>This method uses reflection to determine the generic type parameter {@code M}
     * and creates a Moshi-based encoding strategy that can handle the automatic
     * conversion between the strongly-typed message objects and their JSON string
     * representations.</p>
     *
     * @return a new instance of {@link StringEncodingStrategy} configured for type {@code M}
     */
    protected StringEncodingStrategy<M> createEncodingStrategy() {
        final type = TypeHelper.getGenericType(this, 0)
        return new MoshiEncodeStrategy<M>(type) {}
    }

}
