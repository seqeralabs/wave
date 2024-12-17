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

package io.seqera.wave.tower.client.cache

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import groovy.transform.CompileStatic
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.encoder.MoshiExchange
import io.seqera.wave.store.cache.AbstractTieredCache
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.CredentialsDescription
import io.seqera.wave.tower.client.GetCredentialsKeysResponse
import io.seqera.wave.tower.client.ListCredentialsResponse
import io.seqera.wave.tower.client.UserInfoResponse
import io.seqera.wave.tower.compute.ComputeEnv
import io.seqera.wave.tower.compute.DescribeWorkflowLaunchResponse
import io.seqera.wave.tower.compute.WorkflowLaunchResponse

/**
 * Implements a {@link MoshiEncodeStrategy} for pairing client caches
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class ClientEncoder {

    static MoshiEncodeStrategy instance() {
        new MoshiEncodeStrategy<AbstractTieredCache.Payload>(factory()) {}
    }

    static JsonAdapter.Factory factory() {
        PolymorphicJsonAdapterFactory.of(MoshiExchange.class, "@type")
                .withSubtype(AbstractTieredCache.Payload.class, AbstractTieredCache.Payload.simpleName)
                // add all exchange classes used by the tower client
                .withSubtype(ComputeEnv.class, ComputeEnv.simpleName)
                .withSubtype(CredentialsDescription.class, CredentialsDescription.simpleName)
                .withSubtype(DescribeWorkflowLaunchResponse.class, DescribeWorkflowLaunchResponse.simpleName)
                .withSubtype(GetCredentialsKeysResponse.class, GetCredentialsKeysResponse.simpleName)
                .withSubtype(ListCredentialsResponse.class, ListCredentialsResponse.simpleName)
                .withSubtype(UserInfoResponse.class, UserInfoResponse.simpleName)
                .withSubtype(User.class, User.simpleName)
                .withSubtype(WorkflowLaunchResponse.class, WorkflowLaunchResponse.simpleName)
    }
}
