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


import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.data.queue.MessageQueue
import jakarta.inject.Singleton
/**
 * Model a FIFO queue that accumulates job requests waiting to be submitted
 * to the {@link JobProcessingQueue} accordingly the availability of the latter.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class JobPendingQueue {

    private final static String QUEUE_NAME = 'jobs-pending/v1'

    private MessageQueue<String> delegate

    private EncodingStrategy<JobSpec> encoder

    JobPendingQueue(MessageQueue<String> queue) {
        this.delegate = queue
        this.encoder = new MoshiEncodeStrategy<JobSpec>() {}
        log.debug "Created jobs processing queue"
    }

    void submit(JobSpec request) {
        delegate.offer(QUEUE_NAME, encoder.encode(request))
    }

    JobSpec poll() {
        final result = delegate.poll(QUEUE_NAME)
        return result!=null ? encoder.decode(result) : null
    }

    int length() {
        return delegate.length(QUEUE_NAME)
    }

}
