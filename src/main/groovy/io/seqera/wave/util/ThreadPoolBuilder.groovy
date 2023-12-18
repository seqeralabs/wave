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

package io.seqera.wave.util



import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.RejectedExecutionHandler
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
/**
 * Builder class to create instance of {@link ThreadPoolExecutor}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@CompileStatic
@Deprecated
class ThreadPoolBuilder {

    static AtomicInteger poolCount = new AtomicInteger()

    private String name

    private int minSize

    private int maxSize

    private BlockingQueue<Runnable> workQueue

    private int queueSize = -1

    private Long keepAliveTime

    private RejectedExecutionHandler rejectionPolicy

    private ThreadFactory threadFactory

    private boolean allowCoreThreadTimeout

    String getName() { name }

    int getMinSize() { minSize }

    int getMaxSize() { maxSize }

    int getQueueSize() { queueSize }

    BlockingQueue<Runnable> getWorkQueue() { workQueue }

    Long getKeepAliveTime() { keepAliveTime }

    RejectedExecutionHandler getRejectionPolicy() { rejectionPolicy }

    ThreadFactory getThreadFactory() { threadFactory }

    boolean getAllowCoreThreadTimeout() { allowCoreThreadTimeout }

    ThreadPoolBuilder withName(String name) {
        if( name ) {
            this.name = name
            this.threadFactory = new CustomThreadFactory(name)
        }
        return this
    }

    ThreadPoolBuilder withThreadFactory(ThreadFactory threadFactory) {
        assert !name || !threadFactory, "Property 'threadFactory' or 'name' was already set"
        this.threadFactory = threadFactory
        return this
    }

    ThreadPoolBuilder withRejectionPolicy(RejectedExecutionHandler rejectionPolicy) {
        this.rejectionPolicy = rejectionPolicy
        return this
    }

    ThreadPoolBuilder withMinSize(int min) {
        this.minSize = min
        return this
    }

    ThreadPoolBuilder withMaxSize(int max) {
        this.maxSize = max
        return this
    }

    ThreadPoolBuilder withQueueSize(int size) {
        this.queueSize = size
        this.workQueue = new LinkedBlockingQueue<Runnable>(size)
        return this
    }

    ThreadPoolBuilder withQueue(BlockingQueue<Runnable> workQueue) {
        this.workQueue = workQueue
        return this
    }

    ThreadPoolBuilder withKeepAliveTime( long millis ) {
        keepAliveTime = millis
        return this
    }

    ThreadPoolBuilder withAllowCoreThreadTimeout(boolean flag) {
        this.allowCoreThreadTimeout = flag
        return this
    }

    ThreadPoolExecutor build() {
        assert minSize <= maxSize

        if( !name )
            name = "nf-thread-pool-${poolCount.getAndIncrement()}"

        if(keepAliveTime==null)
            keepAliveTime = 60_000
        if( workQueue==null )
            workQueue = new LinkedBlockingQueue<>()
        if( rejectionPolicy==null )
            rejectionPolicy = new ThreadPoolExecutor.CallerRunsPolicy()
        if( threadFactory==null )
            threadFactory = new CustomThreadFactory(name)

        log.debug "Creating thread pool '$name' minSize=$minSize; maxSize=$maxSize; workQueue=${workQueue.getClass().getSimpleName()}[${queueSize}]; allowCoreThreadTimeout=$allowCoreThreadTimeout"

        final result = new ThreadPoolExecutor(
                minSize,
                maxSize,
                keepAliveTime, TimeUnit.MILLISECONDS,
                workQueue,
                threadFactory,
                rejectionPolicy)

        result.allowCoreThreadTimeOut(allowCoreThreadTimeout)

        return result
    }


    static ThreadPoolExecutor io(String name=null) {
        io(10, 100, 10_000, name)
    }


    static ThreadPoolExecutor io(int min, int max, int queue, String name=null) {
        new ThreadPoolBuilder()
                .withMinSize(min)
                .withMaxSize(max)
                .withQueueSize(queue)
                .withName(name)
                .build()
    }

}
