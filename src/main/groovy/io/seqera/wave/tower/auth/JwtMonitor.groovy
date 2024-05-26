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
        final jwt = authStore.get(key)
        if( !jwt ) {
            log.warn "JWT record not found for key: $key"
            return
        }
        // ignore record without an empty refresh field
        if( !jwt.refresh ) {
            log.debug "JWT record refresh ignored - $jwt"
            return
        }
        // check that's a `createdAt` field (itr may be missing in legacy records)
        if( !jwt.createdAt ) {
            log.warn "JWT record has no receivedAt timestamp - $jwt"
            return
        }
        // i
        final deadline = jwt.createdAt + tokenConfig.cache.duration
        if( now > deadline ) {
            log.debug "JWT record expired - $jwt"
            return
        }

        log.debug "JWT record refresh attempt - $jwt"
        towerClient.userInfo(jwt.endpoint, jwt)
        jwtTimeStore.setRefreshTimer(key)
    }

}
