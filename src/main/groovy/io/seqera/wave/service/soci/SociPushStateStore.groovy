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
 * Store the state of SOCI-enabled tag publications, by SOCI image
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@Requires(bean = SociEnabled)
@CompileStatic
class SociPushStateStore extends AbstractStateStore<SociPushEntry> {

    @Inject
    private SociConfig config

    SociPushStateStore(StateProvider<String,String> provider) {
        super(provider, new MoshiEncodeStrategy<SociPushEntry>() {})
    }

    @Override
    protected String getPrefix() {
        return 'wave-soci-push/v1'
    }

    /** the max wait before retrying a publication failing repeatedly */
    static final Duration MAX_FAILURE_BACKOFF = Duration.ofDays(1)

    /** how long a published SOCI tag is assumed current, before checking it again on the registry */
    static final Duration CURRENT_DURATION = Duration.ofMinutes(5)

    /**
     * A pending publication is dropped after the publish timeout and its push job, so nothing is held for ever
     */
    @Override
    protected Duration getDuration() {
        return config.publishTimeout.plus(config.jobTimeout)
    }

    /**
     * Store a publication with a TTL depending on its status: a succeeded one is kept as the cache of the
     * SOCI tag existence, a failed one twice its backoff, so that the failure count carries over to the
     * next attempt, and a container without layer to index is not checked again for the zTOC cache duration
     */
    void putEntry(SociPushEntry entry) {
        final ttl = switch (entry.status) {
            case SociPushEntry.Status.SUCCEEDED -> CURRENT_DURATION
            case SociPushEntry.Status.FAILED -> failureBackoff(entry.failures).multipliedBy(2)
            case SociPushEntry.Status.NONE -> config.ztocCacheDuration
            default -> getDuration()
        }
        put(entry.key, entry, ttl)
    }

    /**
     * @return How long a publication failed {@code failures} times in a row is not retried: the failure
     * duration, doubled at each failure, up to {@link #MAX_FAILURE_BACKOFF}
     */
    Duration failureBackoff(int failures) {
        final shift = Math.min(Math.max(failures-1, 0), 16)
        final result = config.failureDuration.multipliedBy(1L << shift)
        return result < MAX_FAILURE_BACKOFF ? result : MAX_FAILURE_BACKOFF
    }

    /**
     * Claim an action on a publication, so that among concurrent requests and replicas only one carries it out
     *
     * @param entry The publication
     * @param action The action e.g. {@code publish}
     * @return {@code true} when this caller claimed the action, {@code false} when another one did already
     */
    boolean claim(SociPushEntry entry, String action) {
        return putIfAbsent("${entry.key}/${action}/${entry.request.requestId}".toString(), entry, getDuration())
    }
}
