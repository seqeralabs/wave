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

package io.seqera.wave.service.request

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.store.range.AbstractRangeStore
import io.seqera.wave.store.range.impl.RangeProvider
import jakarta.inject.Singleton
/**
 * Model a range store for container request ids.
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ContainerRequestRange extends AbstractRangeStore {

    @Canonical
    static class Entry {
        final String requestId
        final String workflowId
        final Instant expiration

        Entry withExpiration(Instant instant) {
            new Entry(requestId, workflowId, instant)
        }
    }


    private MoshiEncodeStrategy<Entry> encoder

    ContainerRequestRange(RangeProvider provider) {
        super(provider)
        encoder = new MoshiEncodeStrategy<Entry>() {}
    }

    @Override
    protected String getKey() {
        return 'container-requests-range/v1'
    }

    void add(Entry entry, Duration future) {
        assert future
        add(entry, Instant.now().plus(future))
    }

    void add(Entry entry, Instant expire) {
        assert entry
        assert expire
        super.add(encoder.encode(entry), expire.epochSecond)
    }

    List<Entry> getEntriesUntil(Instant instant, int max) {
        final result = getRange(0, instant.epochSecond, max)
        return result ? result.collect((json)-> encoder.decode(json)) : List.<Entry>of()
    }

}
