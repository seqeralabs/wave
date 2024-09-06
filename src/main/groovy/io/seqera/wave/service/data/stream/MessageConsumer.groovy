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

/**
 * Defines the message consumer interface
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface MessageConsumer<T> {

    /**
     * Consume a message from the stream
     *
     * @param message
     *      A message payload ready to be consumed
     * @return
     *      {@link true} to acknowledge the consumer has effectively consumed the message,
     *      so that it's not made available to other consumers. {@link false} the message
     *      has been consumed, therefore other consumers will ultimately receve it.
     */
    boolean accept(T message)

}
