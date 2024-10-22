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

package io.seqera.wave.service.data.stream.impl

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingQueue

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.stream.MessageConsumer
import io.seqera.wave.service.data.stream.MessageStream
import jakarta.inject.Singleton
/**
 * Implement a {@link MessageStream} using a Java {@link java.util.concurrent.BlockingQueue}.
 * This is only meant for developing purpose.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Requires(notEnv = 'redis')
@Singleton
@CompileStatic
class LocalMessageStream implements MessageStream<String> {

    private ConcurrentHashMap<String, LinkedBlockingQueue<String>> delegate = new ConcurrentHashMap<>()

    @Override
    void init(String streamId) {
        delegate.put(streamId, new LinkedBlockingQueue<>())
    }

    @Override
    void offer(String streamId, String message) {
        delegate
                .get(streamId)
                .offer(message)
    }

    @Override
    boolean consume(String streamId, MessageConsumer<String> consumer) {
        final message = delegate
                .get(streamId)
                .poll()
        if( message==null ) {
            return false
        }

        def result = false
        try {
            result = consumer.accept(message)
        }
        catch (Throwable e) {
            result = false
            throw e
        }
        finally {
            if( !result ) {
                // add again message not consumed to mimic the behavior or redis stream
                sleep(1_000)
                offer(streamId,message)
            }
            return result
        }
    }

}
