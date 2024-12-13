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

package io.seqera.wave.redis

import groovy.transform.CompileStatic;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import redis.clients.jedis.JedisPool;

/**
 * Implements {@link MeterBinder} for {@link redis.clients.jedis.JedisPool}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
class JedisPoolMetricsBinder implements MeterBinder {

    private final JedisPool pool

    JedisPoolMetricsBinder(JedisPool pool) {
        this.pool = pool;
    }

    @Override
    void bindTo(MeterRegistry registry) {
        registry.gauge("jedis.pool.active", pool, JedisPool::getNumActive);
        registry.gauge("jedis.pool.idle", pool, JedisPool::getNumIdle);
        registry.gauge("jedis.pool.waiters", pool, JedisPool::getNumWaiters);

        // Connection lifecycle metrics
        registry.gauge("jedis.pool.created", pool, JedisPool::getCreatedCount);
        registry.gauge("jedis.pool.destroyed", pool, JedisPool::getDestroyedCount);

        // Borrow/Return statistics
        registry.gauge("jedis.pool.borrowed", pool, JedisPool::getBorrowedCount);
        registry.gauge("jedis.pool.returned", pool, JedisPool::getReturnedCount);

        // Additional metrics (resets, evictions, etc.)
        registry.gauge("jedis.pool.max.borrow.wait.millis", pool, (p)-> p.maxBorrowWaitDuration.toMillis() as double)
        registry.gauge("jedis.pool.mean.borrow.wait.millis", pool, (p)-> p.meanBorrowWaitDuration.toMillis() as double)
        registry.gauge("jedis.pool.mean.active.millis", pool, (p)-> p.meanActiveDuration.toMillis() as double)
        registry.gauge("jedis.pool.mean.idle.millis", pool, (p)-> p.meanIdleDuration.toMillis() as double)
    }
}
