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
