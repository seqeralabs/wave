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

package io.seqera.wave.service.job

import java.time.Instant

import com.google.common.hash.Hashing
import groovy.transform.Canonical

/**
 * Model a job id
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
class JobId {
    enum Type { Transfer, Build, Scan }

    final Type type
    final String id
    final Instant creationTime
    final String schedulerId

    JobId( Type type, String id, Instant creationTime ) {
        this.type = type
        this.id = id
        this.creationTime = creationTime
        schedulerId = generate(type, id, creationTime)
    }

    static JobId transfer(String id) {
        new JobId(Type.Transfer, id, Instant.now())
    }

    static JobId build(String id) {
        new JobId(Type.Build, id, Instant.now())
    }

    static JobId scan(String id) {
        new JobId(Type.Scan, id, Instant.now())
    }

    static private String generate(Type type, String id, Instant creationTime) {
        final prefix = type.toString().toLowerCase()
        return prefix + '-' + Hashing
                .sipHash24()
                .newHasher()
                .putUnencodedChars(id)
                .putUnencodedChars(type.toString())
                .putUnencodedChars(creationTime.toString())
                .hash()
    }

}
