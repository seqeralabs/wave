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
import io.seqera.wave.encoder.MoshiEncodeStrategy

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
abstract class BaseMessageQueue<M> extends AbstractMessageQueue<M> {

    BaseMessageQueue(MessageQueue<String> broker, ExecutorService ioExecutor) {
        super(broker, ioExecutor)
    }

    /**
     * Create an instance of the required {@link StringEncodingStrategy<M>} to serialise/deserialize
     * message events.
     *
     * @return An instance of {@link StringEncodingStrategy<M>}
     */
    protected StringEncodingStrategy<M> createEncodingStrategy() {
        final type = TypeHelper.getGenericType(this, 0)
        return new MoshiEncodeStrategy<M>(type) {}
    }

}
