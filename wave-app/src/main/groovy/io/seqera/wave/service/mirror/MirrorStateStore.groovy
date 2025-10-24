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

package io.seqera.wave.service.mirror

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.wave.configuration.BuildEnabled
import io.seqera.wave.configuration.MirrorConfig
import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.wave.store.state.AbstractStateStore
import io.seqera.wave.store.state.impl.StateProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implement a {@link io.seqera.wave.store.state.StateStore} for {@link MirrorEntry} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Requires(bean = BuildEnabled)
@CompileStatic
class MirrorStateStore extends AbstractStateStore<MirrorEntry> {

    @Inject
    private MirrorConfig config

    MirrorStateStore(StateProvider<String,String> provider) {
        super(provider, new MoshiEncodeStrategy<MirrorEntry>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-mirror/v1'
    }

    @Override
    protected Duration getDuration() {
        return config.statusDuration
    }

    void putEntry(MirrorEntry entry) {
        if( !entry.result )
            throw new IllegalArgumentException("Missing mirror entry result - offending value=$entry")
        // use a short time-to-live for failed build
        // this is needed to allow re-try builds failed for
        // temporary error conditions e.g. expired credentials
        final ttl = entry.result.succeeded()
                ? config.statusDuration
                : config.failureDuration
        put(entry.key, entry, ttl)
    }

    MirrorEntry awaitCompletion(String targetImage) {
        final beg = System.currentTimeMillis()
        while( true ) {
            final result = get(targetImage)
            // missing record
            if( !result )
                throw new IllegalStateException("Unknown mirror container $targetImage")
            // ok done
            if( result.done() )
                return result
            Thread.sleep(config.statusDelay)
            // timeout the request
            if( System.currentTimeMillis()-beg > config.statusDuration.toMillis() )
                throw new IllegalStateException("Timeout mirror container $targetImage")
        }
    }
}
