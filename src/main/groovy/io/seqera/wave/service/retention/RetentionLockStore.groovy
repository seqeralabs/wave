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

package io.seqera.wave.service.retention

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Requires
import io.seqera.data.store.state.AbstractStateStore
import io.seqera.data.store.state.impl.StateProvider
import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.wave.configuration.RetentionConfig
import io.seqera.wave.configuration.RetentionEnabled
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Elects the single replica that runs a retention pass. {@code putIfAbsent} is a Redis
 * {@code SET NX PX}, so only one caller wins and the lease expires on its own if that replica
 * dies mid-pass.
 *
 * Note this is leader election, unlike the other {@code putIfAbsent} users in Wave, which dedup
 * per work item - a retention pass has no work item to key on.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Requires(bean = RetentionEnabled)
@Singleton
@CompileStatic
class RetentionLockStore extends AbstractStateStore<String> {

    @Inject
    private RetentionConfig config

    RetentionLockStore(StateProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<String>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-retention-lock/v1'
    }

    /**
     * Bounds the lease when a replica dies mid-pass; the happy path releases it explicitly,
     * so the TTL never has to line up with the schedule.
     */
    @Override
    protected Duration getDuration() {
        return config.interval
    }

    /**
     * @return {@code true} when this replica acquired the lease and should run the purge
     */
    boolean acquire(String owner) {
        return putIfAbsent(KEY, owner)
    }

    /**
     * Release the lease at the end of a pass. Without this the lease would still be held when
     * the next tick fires, since it expires exactly one interval after it was taken.
     *
     * Only the owner may release: if this pass overran the lease and another replica has since
     * taken it, releasing would hand a third replica the lock while two passes are still running.
     */
    void release(String owner) {
        if( get(KEY) == owner )
            remove(KEY)
        else
            log.warn "Data retention lease is no longer owned by '${owner}' - leaving it to its current holder"
    }

    static private final String KEY = 'retention'

}
