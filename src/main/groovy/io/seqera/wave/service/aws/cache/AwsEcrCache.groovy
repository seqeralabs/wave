/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2024, Seqera Labs
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

package io.seqera.wave.service.aws.cache

import java.time.Duration

import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.adapters.PolymorphicJsonAdapterFactory
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.encoder.MoshiSerializable
import io.seqera.wave.store.cache.AbstractTieredCache
import io.seqera.wave.store.cache.L2TieredCache
import io.seqera.wave.store.cache.TieredKey
import jakarta.inject.Singleton
/**
 * Implement a tiered cache for AWS ECR client
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class AwsEcrCache extends AbstractTieredCache<TieredKey, AwsEcrAuthToken> {

    @Value('${wave.aws.ecr.cache.duration:3h}')
    private Duration duration

    @Value('${wave.aws.ecr.cache.max-size:10000}')
    private int maxSize

    AwsEcrCache(@Nullable L2TieredCache l2) {
        super(l2, encoder())
    }

    @Override
    int getMaxSize() {
        return maxSize
    }

    @Override
    protected getName() {
        return 'aws-ecr-cache'
    }

    @Override
    protected String getPrefix() {
        return 'aws-ecr-cache/v1'
    }

    static MoshiEncodeStrategy encoder() {
        new MoshiEncodeStrategy<AbstractTieredCache.Entry>(factory()) {}
    }

    static JsonAdapter.Factory factory() {
        PolymorphicJsonAdapterFactory.of(MoshiSerializable.class, "@type")
                .withSubtype(AbstractTieredCache.Entry.class, AbstractTieredCache.Entry.name)
                .withSubtype(AwsEcrAuthToken.class, AwsEcrAuthToken.simpleName)
    }

}
