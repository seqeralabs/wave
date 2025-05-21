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

package io.seqera.wave.service.job

import io.seqera.wave.configuration.JobManagerConfig

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.service.data.stream.AbstractMessageStream
import io.seqera.wave.service.data.stream.MessageConsumer
import io.seqera.wave.service.data.stream.MessageStream
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
/**
 * Implements a simple persistent FIFO queue
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class JobProcessingQueue extends AbstractMessageStream<JobSpec> {

    private final static String QUEUE_NAME = "jobs-queue"

    private final static String STREAM_NAME = "jobs-queue/v1"

    private final JobManagerConfig config

    JobProcessingQueue(MessageStream<String> target, JobManagerConfig config) {
        super(target)
        this.config = config
        log.info "Created jobs processing queue - config=${config}"
    }

    @Override
    protected String name() {
        return QUEUE_NAME
    }

    @Override
    protected Duration pollInterval() {
        return config.pollInterval
    }

    final void offer(JobSpec jobSpec) {
        super.offer(STREAM_NAME, jobSpec)
    }

    final void addConsumer(MessageConsumer<JobSpec> consumer) {
        super.addConsumer(STREAM_NAME, consumer)
    }

    final int length() {
        return super.length(STREAM_NAME)
    }

    @PreDestroy
    void destroy() {
        log.debug "Shutting down jobs processing queue"
        this.close()
    }
}
