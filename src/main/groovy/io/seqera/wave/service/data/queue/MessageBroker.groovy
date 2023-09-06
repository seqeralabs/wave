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

    /**
     * Create a mark with for the specified key
     *
     * @param key
     *      The mark unique key
     */
    void mark(String key)

    /**
     * Remove the mark from the specified key
     *
     * @param key
     *      The mark unique key
     */
    void unmark(String key)

    /**
     * Check if one or more marks with the specified prefix exists
     *
     * @param prefix
     *      The prefix of the mark key
     * @return
     *      {@code true} when one or more marks exist with a key matching the specified prefix,
     *      {@code false} otherwise
     */
    boolean hasMark(String prefix)
}




