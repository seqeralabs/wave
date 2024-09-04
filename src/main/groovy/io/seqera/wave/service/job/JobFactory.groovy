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
import javax.annotation.Nullable

import com.google.common.hash.Hashing
import groovy.transform.CompileStatic
import io.seqera.wave.configuration.BlobCacheConfig
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.job.spec.BuildJobSpec
import io.seqera.wave.service.job.spec.TransferJobSpec
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Simple factory for {@link JobSpec} objects
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class JobFactory {

    @Inject
    @Nullable
    private BlobCacheConfig blobConfig

    TransferJobSpec transfer(String id) {
        final ts = Instant.now()
        return new TransferJobSpec(
                id,
                ts,
                blobConfig.transferTimeout,
                generate("transfer", id, ts)
        )
    }

    BuildJobSpec build(BuildRequest request) {
        return new BuildJobSpec(
                request.targetImage,
                request.startTime,
                request.maxDuration,
                "build-" + request.buildId.replace('_', '-'),
                request.buildId,
                request.targetImage,
                request.workDir
        )
    }

    static private String generate(String type, String id, Instant creationTime) {
        final prefix = type.toLowerCase()
        return prefix + '-' + Hashing
                .sipHash24()
                .newHasher()
                .putUnencodedChars(id)
                .putUnencodedChars(type.toString())
                .putUnencodedChars(creationTime.toString())
                .hash()
    }

}
