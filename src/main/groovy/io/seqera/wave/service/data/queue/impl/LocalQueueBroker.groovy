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

package io.seqera.wave.service.data.queue.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.queue.MessageBroker
import jakarta.inject.Singleton
/**
 * Implement a message broker based on a simple blocking queue.
 * This is only meant for local/development purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(notEnv = 'redis')
@Singleton
@CompileStatic
class LocalQueueBroker implements MessageBroker<String> {

    private ConcurrentHashMap<String, LinkedBlockingQueue<String>> store = new ConcurrentHashMap<>()

    private ConcurrentHashMap<String, Boolean> marks = new ConcurrentHashMap<>()

    @Override
    void offer(String target, String message) {
        store
            .computeIfAbsent(target, (it)->new LinkedBlockingQueue<String>())
            .offer(message)
    }

    @Override
    String take(String target) {
        store
            .computeIfAbsent(target, (it)->new LinkedBlockingQueue<String>())
            .poll()
    }

    @Override
    void mark(String key) {
        marks.put(key, true)
    }

    @Override
    void unmark(String key) {
        marks.remove(key)
    }

    @Override
    boolean hasMark(String prefix) {
        marks.keySet().find((String it) -> it.startsWith(prefix))
    }
}
