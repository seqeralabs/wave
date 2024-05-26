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
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Context
@CompileStatic
class JwtHeartbeat implements Runnable {

    @Inject
    private JwtAuthStore authStore

    @Inject
    private TowerClient towerClient

    @Inject
    private TaskScheduler taskScheduler

    @Inject
    private JwtTimer jwtTimer

    @Inject
    TaskScheduler scheduler

    @Inject
    private JwtConfig jwtConfig

    @Inject
    private TokenConfig tokenConfig

    @PostConstruct
    private init() {
        log.info "Creating JWT heartbeat - $jwtConfig"
        scheduler.scheduleAtFixedRate(jwtConfig.heartbeatDelay, jwtConfig.heartbeatInterval, this)
    }

    void run() {
        final now = Instant.now()
        final allKeys = jwtTimer.getRange(0, now.epochSecond, 10)
        for( String key : allKeys ) {
            // get the jwt info the given "timer" and refresh it
            final jwt = authStore.get(key)
            if( !jwt ) {
                log.warn "JWT record not found for key - $jwt"
                continue
            }
            if( !jwt.createdAt ) {
                log.warn "JWT record has no receivedAt timestamp - $jwt"
                continue
            }
            final deadline = jwt.createdAt + tokenConfig.cache.duration
            if( now > deadline ) {
                log.debug "JWT record expired - $jwt"
                continue
            }

            if( jwt.refresh ) {
                log.debug "JWT record refresh attempt - $jwt"
                towerClient.userInfo(jwt.endpoint, jwt)
                jwtTimer.setRefreshTimer(key)
            }
            else {
                log.debug "JWT record refresh ignored - $jwt"
            }
        }
    }

}
