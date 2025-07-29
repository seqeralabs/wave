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

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.data.stream.MessageConsumer
import io.seqera.data.stream.MessageStream
import io.seqera.serde.encode.StringEncodingStrategy
import io.seqera.wave.configuration.JobManagerConfig
import io.seqera.wave.configuration.WaveLite
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.data.stream.BaseMessageStream
import jakarta.annotation.PreDestroy
import jakarta.inject.Singleton
/**
 * Model a FIFO queue that accumulates job requests waiting to be submitted
 * to the {@link JobProcessingQueue} accordingly the availability of the latter.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(missingBeans = WaveLite)
@Slf4j
@Singleton
@CompileStatic
class JobPendingQueue extends BaseMessageStream<JobSpec> {

    private final static String STREAM_NAME = 'jobs-pending/v2'

    private StringEncodingStrategy<JobSpec> encoder

    private JobManagerConfig config

    JobPendingQueue(MessageStream<String> target, JobManagerConfig config) {
        super(target)
        this.encoder = new MoshiEncodeStrategy<JobSpec>() {}
        this.config = config
        log.info "Created jobs pending queue - config=${config}"
    }

    @Override
    protected String name() {
        return "jobs-pending"
    }

    @Override
    protected Duration pollInterval() {
        return config.schedulerInterval
    }

    final void submit(JobSpec jobSpec) {
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
        log.debug "Shutting down jobs pending queue"
        this.close()
    }
}
