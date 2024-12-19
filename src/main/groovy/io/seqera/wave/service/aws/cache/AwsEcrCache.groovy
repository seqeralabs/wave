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

package io.seqera.wave.service.aws.cache

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import io.seqera.wave.store.cache.AbstractTieredCache
import io.seqera.wave.store.cache.L2TieredCache
import io.seqera.wave.tower.client.cache.ClientEncoder
import jakarta.inject.Singleton

/**
 * Implement a tiered cache for AWS ECR client
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Singleton
@CompileStatic
class AwsEcrCache extends AbstractTieredCache {
    AwsEcrCache(@Nullable L2TieredCache l2,
                @Value('${wave.aws.ecr.cache.duration:3h}') Duration duration,
                @Value('${wave.aws.ecr.cache.max-size:10000}') int maxSize)
    {
        super(l2, ClientEncoder.instance(), duration, maxSize)
    }

    @Override
    protected getName() {
        return 'aws-ecr-cache'
    }

    @Override
    protected String getPrefix() {
        return 'aws-ecr-cache/v1'
    }
}
