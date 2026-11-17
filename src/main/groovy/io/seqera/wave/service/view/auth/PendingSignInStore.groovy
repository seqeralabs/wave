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

package io.seqera.wave.service.view.auth

import java.time.Duration

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.seqera.data.store.state.AbstractStateStore
import io.seqera.data.store.state.impl.StateProvider
import io.seqera.serde.moshi.MoshiEncodeStrategy
import jakarta.inject.Singleton

/**
 * Holds a sign-in in flight, keyed by its {@code state}, for the few minutes a redirect takes.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Singleton
@CompileStatic
class PendingSignInStore extends AbstractStateStore<PendingSignIn> {

    @Value('${wave.view.auth.pending-duration:5m}')
    private Duration duration

    PendingSignInStore(StateProvider<String, String> provider) {
        super(provider, new MoshiEncodeStrategy<PendingSignIn>() {})
    }

    @Override
    protected String getPrefix() { return 'wave-view-signin/v1' }

    @Override
    protected Duration getDuration() { return duration }

}
