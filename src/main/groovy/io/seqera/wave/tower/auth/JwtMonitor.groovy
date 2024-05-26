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

package io.seqera.wave.tower.auth

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.scheduling.TaskScheduler
import io.seqera.wave.configuration.TokenConfig
import io.seqera.wave.tower.client.TowerClient
import jakarta.annotation.PostConstruct
import jakarta.inject.Inject
/**
 * Implement a service that monitor JWT token record and
 * periodically refresh them to avoid they expiry
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Context
@CompileStatic
class JwtMonitor implements Runnable {

    @Inject
    private JwtAuthStore authStore

    @Inject
    private TowerClient towerClient

    @Inject
    private TaskScheduler taskScheduler

    @Inject
    private JwtTimeStore jwtTimeStore

    @Inject
    private TaskScheduler scheduler

    @Inject
    private JwtConfig jwtConfig

    @Inject
    private TokenConfig tokenConfig

    @PostConstruct
    private init() {
        log.info "Creating JWT heartbeat - $jwtConfig"
        scheduler.scheduleAtFixedRate(jwtConfig.monitorDelay, jwtConfig.monitorInterval, this)
    }

    void run() {
        final now = Instant.now()
        final keys = jwtTimeStore.getRange(0, now.epochSecond, jwtConfig.monitorCount)
        for( String it : keys ) {
            try {
                check0(it, now)
            }
            catch (InterruptedException e) {
                Thread.interrupted()
            }
            catch (Throwable t) {
                log.error("Unexpected error in JWT heartbeat while processing key: $it", t)
            }
        }
    }

    protected void check0(String key, Instant now) {
        log.trace "JWT checking record status for key: $key"

        // get the jwt info the given "timer" and refresh it
        final entry = authStore.get(key)
        if( !entry ) {
            log.warn "JWT record not found for key: $key"
            return
        }
        // ignore record without an empty refresh field
        if( !entry.refresh ) {
            log.info "JWT record refresh ignored - entry=$entry"
            return
        }
        // check that's a `createdAt` field (it may be missing in legacy records)
        if( !entry.createdAt ) {
            log.warn "JWT record has no receivedAt timestamp - entry=$entry"
            return
        }
        // check if the JWT record is expired
        final deadline = entry.createdAt + tokenConfig.cache.duration
        if( now > deadline ) {
            log.info "JWT record expired - entry=$entry; deadline=$deadline; "
            return
        }

        log.debug "JWT refresh request - entry=$entry; deadline=$deadline"
        towerClient.userInfo(entry.endpoint, entry)
        jwtTimeStore.setRefreshTimer(key)
    }

}
