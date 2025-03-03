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

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import groovy.transform.CompileStatic
import io.seqera.wave.encoder.EncodingStrategy
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.encoder.MoshiSerializable
import io.seqera.wave.service.blob.TransferRequest
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.data.queue.MessageQueue
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.scan.ScanRequest
import jakarta.inject.Singleton
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class JobPendingQueue {

    private final static String QUEUE_NAME = 'jobs-pending/v1'

    private MessageQueue<String> delegate

    private EncodingStrategy<JobRequest> encoder

    JobPendingQueue(MessageQueue<String> queue) {
        this.delegate = queue
        this.encoder = encoder()
    }

    void submit(JobRequest request) {
        delegate.offer(QUEUE_NAME, encoder.encode(request))
    }

    JobRequest poll() {
        final result = delegate.poll(QUEUE_NAME)
        return result!=null ? encoder.decode(result) : null
    }

    int length() {
        return delegate.length(QUEUE_NAME)
    }

    static MoshiEncodeStrategy<JobRequest> encoder() {
        new MoshiEncodeStrategy<JobRequest>(factory()) {}
    }

    static private JsonAdapter.Factory factory() {
        PolymorphicJsonAdapterFactory.of(MoshiSerializable.class, "@type")
                .withSubtype(BuildRequest.class, BuildRequest.simpleName)
                .withSubtype(ScanRequest.class, ScanRequest.simpleName)
                .withSubtype(MirrorRequest.class, MirrorRequest.simpleName)
                .withSubtype(TransferRequest.class, TransferRequest.simpleName)
    }
}
