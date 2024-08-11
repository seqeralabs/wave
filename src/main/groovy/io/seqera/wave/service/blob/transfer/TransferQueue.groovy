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

package io.seqera.wave.service.blob.transfer

import java.time.Duration

import io.seqera.wave.service.data.queue.MessageQueue
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
class TransferQueue {

    final private static String QUEUE_NAME = 'transfer-queue/v1'

    @Inject
    private MessageQueue<String> transferQueue

    void offer(String transferId) {
        transferQueue.offer(QUEUE_NAME, transferId)
    }

    String poll(Duration timeout) {
        transferQueue.poll(QUEUE_NAME, timeout)
    }

}
