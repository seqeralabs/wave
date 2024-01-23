/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023, Seqera Labs
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

import groovy.transform.CompileStatic
/**
 * Interface for a message broker modelled as a blocking queue.
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 * @author Paolo Di Tommmaso <paolo.ditommaso@gmail.com>
 * @param <M>    The type of message that can be sent through the broker.
 */
@CompileStatic
interface MessageBroker<M> {

    /**
     * Inserts the specified element at the tail of the specified queue.
     *
     * @param target
     *      The queue unique identified
     * @param value
     *  The value that should be added to the queue
     */
    void offer(String target, M value)

    /**
     * Retrieves and removes the head of this queue, waiting up to the specified wait time if necessary
     * for an element to become available.
     *
     * @param target
     *      The queue unique identifier
     * @param timeout
     *      How long to wait before giving up, in units of unit unit – a TimeUnit determining how to interpret the timeout parameter
     * @return
     *      The head of this queue, or null if the specified waiting time elapses before an element is available
     */
    M take(String target)

}




