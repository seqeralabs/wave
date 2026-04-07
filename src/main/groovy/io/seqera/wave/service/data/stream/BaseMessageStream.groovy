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

package io.seqera.wave.service.data.stream

import io.seqera.data.stream.AbstractMessageStream
import io.seqera.data.stream.MessageStream
import io.seqera.lang.type.TypeHelper
import io.seqera.serde.encode.StringEncodingStrategy
import io.seqera.serde.moshi.MoshiEncodeStrategy

/**
 * Base abstract class for implementing message streams in the Wave application.
 * 
 * <p>This class extends {@link AbstractMessageStream} and provides a foundation for
 * creating type-safe message streams with automatic JSON serialization/deserialization
 * using the Moshi library. It handles the encoding strategy configuration and
 * provides a consistent interface for message stream implementations.</p>
 * 
 * <p>Message streams are used for real-time message processing and event handling,
 * allowing for continuous data flow and stream-based operations. Concrete implementations
 * should extend this class to define specific streaming behavior and message processing logic.</p>
 * 
 * @param <M> the type of messages that this stream will handle
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class BaseMessageStream<M> extends AbstractMessageStream<M> {

    /**
     * Constructs a new BaseMessageStream with the specified target stream.
     * 
     * @param target the underlying string-based message stream that handles
     *               the actual message transport and streaming operations
     */
    BaseMessageStream(MessageStream<String> target) {
        super(target)
    }

    /**
     * Creates an instance of the required {@link StringEncodingStrategy} to serialize
     * and deserialize message events to/from JSON format.
     * 
     * <p>This method uses reflection to determine the generic type parameter {@code M}
     * and creates a Moshi-based encoding strategy that can handle the automatic
     * conversion between the strongly-typed message objects and their JSON string
     * representations for streaming operations.</p>
     * 
     * @return a new instance of {@link StringEncodingStrategy} configured for type {@code M}
     */
    @Override
    protected StringEncodingStrategy<M> createEncodingStrategy() {
        final type = TypeHelper.getGenericType(this, 0)
        return new MoshiEncodeStrategy<M>(type) {}
    }
}
