/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
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


import java.util.function.Consumer
import java.util.function.Predicate

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface MessageStream<M> {

    /**
     * Inserts the specified element at the tail of the specified queue.
     *
     * @param value
     *  The value that should be added to the queue
     */
    void offer(String streamId, M message)

    /**
     * Consume a message from the stream and invoke
     *
     * @param streamId
     *      The target stream name
     * @param consumer
     *      The {@link Consumer} instance to be invoked to consume the message
     * @return
     *      {code true} when the message has been processed successfully, otherwise {@code false}
     *      when the message needs to be further processed
     */
    boolean consume(String streamId, Predicate<M> consumer)

}
