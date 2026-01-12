/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.service.pairing

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.data.store.state.AbstractStateStore
import io.seqera.data.store.state.impl.StateProvider
import jakarta.inject.Inject
import jakarta.inject.Singleton

/**
 * Implements a cache store for {@link PairingRecord}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingStore extends AbstractStateStore<PairingRecord> {

    @Inject
    private PairingConfig config

    PairingStore(StateProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<PairingRecord>() {})
    }

    @Override
    protected String getPrefix() {
        return 'pairing-keys/v1'
    }

    /**
     * @return A duration representing the TTL of the entries in the cache
     */
    @Override
    protected Duration getDuration() {
        // note: the cache store should be modified to allow the support for
        // infinite duration using with null
        return config.keyDuration
    }
}
