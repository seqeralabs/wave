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
package io.seqera.wave.service.persistence

import java.time.Instant
import java.time.temporal.ChronoUnit

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.seqera.wave.service.builder.BuildEvent

/**
 * Model a Wave build count
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class WaveBuildCountRecord {
    String ip
    long userId
    String imageName
    boolean success
    Instant date

    WaveBuildCountRecord(BuildEvent event){
        this.ip = event.request.ip
        if(event.request.user && event.request.user.id){
            this.userId = event.request.user.id
        }else{
            this.userId = -1 //for anonymous users
        }
        this.imageName = event.request.targetImage
        this.success = event.result.succeeded()
        this.date = Instant.now().truncatedTo(ChronoUnit.DAYS)
    }

    WaveBuildCountRecord(String ip, long userId, String imageName, boolean success, Instant date) {
        this.ip = ip
        this.userId = userId
        this.imageName = imageName
        this.success = success
        this.date = date
    }
}
