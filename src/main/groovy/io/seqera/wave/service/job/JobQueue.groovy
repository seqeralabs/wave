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

import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.data.queue.MessageQueue
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements a simple persistent FIFO queue
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class JobQueue {

    final private static String QUEUE_NAME = 'jobs-queue/v1'

    private EncodingStrategy<JobId> encodingStrategy

    @Inject
    private MessageQueue<String> transferQueue

    @PostConstruct
    private init() {
        encodingStrategy = new MoshiEncodeStrategy<JobId>() {}
    }

    void offer(JobId request) {
        transferQueue.offer(QUEUE_NAME, encodingStrategy.encode(request))
    }

    JobId poll(Duration timeout) {
        final result = transferQueue.poll(QUEUE_NAME, timeout)
        return result ? encodingStrategy.decode(result) : null
    }

}
