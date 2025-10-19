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
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.serde.moshi.MoshiEncodeStrategy
import io.seqera.serde.moshi.MoshiSerializable
import io.seqera.wave.store.cache.AbstractTieredCache
import io.seqera.wave.store.cache.L2TieredCache
import io.seqera.wave.tower.User
import io.seqera.wave.tower.client.CredentialsDescription
import io.seqera.wave.tower.client.GetCredentialsKeysResponse
import io.seqera.wave.tower.client.ListCredentialsResponse
import io.seqera.wave.tower.client.GetUserInfoResponse
import io.seqera.wave.tower.compute.ComputeEnv
import io.seqera.wave.tower.compute.DescribeWorkflowLaunchResponse
import io.seqera.wave.tower.compute.WorkflowLaunch
import jakarta.inject.Singleton
/**
 * Implement a client cache having short-term expiration policy
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class ClientCache extends AbstractTieredCache {

    @Value('${wave.pairing.cache.max-size:10000}')
    private int maxSize = 10000

    ClientCache(@Nullable L2TieredCache l2) {
        super(l2, encoder())
    }

    @Override
    protected getName() {
        return 'pairing-cache'
    }

    @Override
    protected String getPrefix() {
        return 'pairing-cache/v1'
    }

    @Override
    int getMaxSize() {
        return maxSize
    }

    static MoshiEncodeStrategy encoder() {
        new MoshiEncodeStrategy<AbstractTieredCache.Entry>(factory()) {}
    }

    static JsonAdapter.Factory factory() {
        PolymorphicJsonAdapterFactory.of(MoshiSerializable.class, "@type")
                .withSubtype(AbstractTieredCache.Entry.class, AbstractTieredCache.Entry.name)
        // add all exchange classes used by the tower client
                .withSubtype(ComputeEnv.class, ComputeEnv.simpleName)
                .withSubtype(CredentialsDescription.class, CredentialsDescription.simpleName)
                .withSubtype(DescribeWorkflowLaunchResponse.class, DescribeWorkflowLaunchResponse.simpleName)
                .withSubtype(GetCredentialsKeysResponse.class, GetCredentialsKeysResponse.simpleName)
                .withSubtype(ListCredentialsResponse.class, ListCredentialsResponse.simpleName)
                .withSubtype(GetUserInfoResponse.class, GetUserInfoResponse.simpleName)
                .withSubtype(User.class, User.simpleName)
                .withSubtype(WorkflowLaunch.class, WorkflowLaunch.simpleName)
        // add legacy classes
                .withSubtype(GetUserInfoResponse.class, 'UserInfoResponse')
                .withSubtype(WorkflowLaunch.class, 'WorkflowLaunchResponse')

    }
}
