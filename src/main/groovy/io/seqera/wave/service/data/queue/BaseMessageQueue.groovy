/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.data.queue

import java.util.concurrent.ExecutorService

import io.seqera.lang.type.TypeHelper
import io.seqera.serde.encode.StringEncodingStrategy
import io.seqera.data.queue.AbstractMessageQueue
import io.seqera.data.queue.MessageQueue
import io.seqera.serde.moshi.MoshiEncodeStrategy

/**
 * Base abstract class for implementing message queues in the Wave application.
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
