/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.soci

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Requires
import io.seqera.data.store.state.AbstractStateStore
import io.seqera.data.store.state.impl.StateProvider
import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.wave.configuration.SociConfig
import io.seqera.wave.configuration.SociEnabled
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Store the state of zTOC indexing jobs, by layer
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Requires(bean = SociEnabled)
@CompileStatic
class SociIndexStateStore extends AbstractStateStore<SociIndexEntry> {

    @Inject
    private SociConfig config

    SociIndexStateStore(StateProvider<String,String> provider) {
        super(provider, new MoshiEncodeStrategy<SociIndexEntry>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-soci-job/v1'
    }

    /**
     * A pending job is kept twice the job timeout, so a lost job does not block the layer for ever
     */
    @Override
    protected Duration getDuration() {
        return config.jobTimeout.multipliedBy(2)
    }

    /**
     * Store a completed job. A failed layer is not re-submitted before the failure duration, so that
     * a layer that cannot be indexed does not churn jobs. A succeeded one is not needed anymore once
     * its zTOC is in the {@link SociZtocStore}
     */
    void putEntry(SociIndexEntry entry) {
        put(entry.key, entry, config.failureDuration)
    }

    /**
     * Wait for the completion of the indexing jobs with the given keys
     *
     * @param keys The keys of the jobs, see {@link SociIndexRequest#key}
     * @return {@code true} when all jobs completed, or {@code false} when the {@code wave.soci.await-timeout} expired
     */
    boolean awaitCompletion(Collection<String> keys) {
        final beg = System.currentTimeMillis()
        while( keys.stream().anyMatch(this::pending) ) {
            if( System.currentTimeMillis()-beg > config.awaitTimeout.toMillis() )
                return false
            Thread.sleep(config.statusDelay.toMillis())
        }
        return true
    }

    protected boolean pending(String key) {
        final entry = get(key)
        return entry!=null && !entry.done()
    }
}
