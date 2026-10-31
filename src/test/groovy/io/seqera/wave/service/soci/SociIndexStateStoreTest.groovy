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

import spock.lang.Specification

import java.time.Duration
import java.util.concurrent.CompletableFuture

import io.seqera.data.store.state.impl.LocalStateProvider
import io.seqera.wave.configuration.SociConfig
/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
class SociIndexStateStoreTest extends Specification {

    static final String LAYER = 'sha256:' + '1'*64

    SociIndexStateStore store(Duration timeout) {
        def config = new SociConfig(jobTimeout: Duration.ofMinutes(10), failureDuration: Duration.ofMinutes(10),
                awaitTimeout: timeout, statusDelay: Duration.ofMillis(50))
        def store = new SociIndexStateStore(new LocalStateProvider())
        store.@config = config
        return store
    }

    def 'should await the completion of the indexing jobs' () {
        given:
        def store = store(Duration.ofSeconds(5))
        def request = new SociIndexRequest(requestId: 'soci-1', layerDigest: LAYER, spanSize: 4194304)
        def entry = SociIndexEntry.of(request)
        store.put(entry.key, entry)

        when:
        def result = CompletableFuture.supplyAsync(() -> store.awaitCompletion([entry.key, 'missing']))
        sleep 200
        then:
        !result.isDone()

        when:
        store.putEntry(entry.complete(0, null, null))
        then:
        result.get()
    }

    def 'should time out when a job does not complete' () {
        given:
        def store = store(Duration.ofMillis(200))
        def entry = SociIndexEntry.of(new SociIndexRequest(requestId: 'soci-1', layerDigest: LAYER, spanSize: 4194304))
        store.put(entry.key, entry)

        expect:
        !store.awaitCompletion([entry.key])
    }
}
