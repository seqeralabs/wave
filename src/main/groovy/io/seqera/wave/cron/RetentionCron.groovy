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

package io.seqera.wave.cron

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Requires
import io.micronaut.scheduling.annotation.Scheduled
import io.seqera.wave.configuration.RetentionConfig
import io.seqera.wave.configuration.RetentionEnabled
import io.seqera.wave.service.retention.RetentionLockStore
import io.seqera.wave.service.retention.RetentionService
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
/**
 * Run the data retention purge periodically.
 *
 * Only the replica that takes the Redis lease runs a pass; the purge itself is idempotent, so
 * the lease is there to stop every replica scanning the same tables rather than to protect
 * correctness.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Requires(bean = RetentionEnabled)
@Context
@CompileStatic
class RetentionCron {

    @Inject
    private RetentionConfig config

    @Inject
    private RetentionService retentionService

    @Inject
    private RetentionLockStore lockStore

    private String owner

    @PostConstruct
    void init0() {
        owner = "${hostName()}/${ProcessHandle.current().pid()}"
        log.info "Creating data retention cron - owner: ${owner}"
    }

    // only identifies the lease holder in logs, so never resolve DNS or fail startup for it
    static private String hostName() {
        return System.getenv('HOSTNAME') ?: UUID.randomUUID().toString().take(8)
    }

    @Scheduled(fixedRate = '${wave.retention.interval:1h}', initialDelay = '${wave.retention.interval:1h}')
    void purge() {
        if( !lockStore.acquire(owner) ) {
            log.debug "Data retention pass already owned by another replica - skipping"
            return
        }
        try {
            final count = retentionService.purge()
            log.debug "Data retention pass completed - removed ${count} record(s)"
        }
        catch (Exception e) {
            log.error "Data retention pass failed - cause: ${e.message}", e
        }
        finally {
            lockStore.release(owner)
        }
    }

}
