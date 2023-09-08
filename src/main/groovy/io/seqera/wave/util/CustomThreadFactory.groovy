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

package io.seqera.wave.util

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic

import java.lang.Thread.UncaughtExceptionHandler

import groovy.util.logging.Slf4j

/**
 * A customised thread factory
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
class CustomThreadFactory implements ThreadFactory {

    private ThreadGroup group

    private AtomicInteger threadNumber = new AtomicInteger(1)

    private UncaughtExceptionHandler exceptionHandler

    private prefix

    CustomThreadFactory(String prefix, UncaughtExceptionHandler exceptionHandler=null) {
        this.prefix = prefix ?: 'wave-thread'
        this.group = System.getSecurityManager()?.getThreadGroup() ?: Thread.currentThread().getThreadGroup()
        this.exceptionHandler = exceptionHandler
    }


    Thread newThread(Runnable r) {
        final name = "${prefix}-${threadNumber.getAndIncrement()}"
        log.trace "Creating thread: $name"

        def thread = new Thread(group, r, name, 0)
        if (thread.isDaemon())
            thread.setDaemon(false);
        if (thread.getPriority() != Thread.NORM_PRIORITY)
            thread.setPriority(Thread.NORM_PRIORITY)
        if( exceptionHandler )
            thread.setUncaughtExceptionHandler(exceptionHandler)
        return thread
    }
}
