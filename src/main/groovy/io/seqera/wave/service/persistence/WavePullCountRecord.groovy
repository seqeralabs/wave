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
import io.seqera.wave.service.metric.event.PullEvent

/**
 * Model a Wave pull count
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@ToString(includeNames = true, includePackage = false)
@EqualsAndHashCode
@CompileStatic
class WavePullCountRecord {
    String ip
    long userId
    String imageName
    Instant date

    WavePullCountRecord(PullEvent event){
        this.ip = event.ip
        this.userId = event.request.userId?:-1 //for anonymous users
        this.imageName = event.request.containerImage
        this.date = Instant.now().truncatedTo(ChronoUnit.DAYS)
    }

    WavePullCountRecord(String ip, long userId, String imageName, Instant date) {
        this.ip = ip
        this.userId = userId
        this.imageName = imageName
        this.date = date
    }
}
