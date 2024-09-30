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
import io.seqera.wave.configuration.ScanConfig
import io.seqera.wave.service.builder.BuildRequest
import io.seqera.wave.service.mirror.MirrorConfig
import io.seqera.wave.service.mirror.MirrorRequest
import io.seqera.wave.service.scan.ScanRequest
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
  
    @Inject
    @Nullable
    private ScanConfig scanConfig

    @Inject
    @Nullable
    private MirrorConfig mirrorConfig

    JobSpec transfer(String stateId) {
        JobSpec.transfer(
                stateId,
                generate("transfer", stateId, Instant.now()),
                Instant.now(),
                blobConfig.transferTimeout,
        )
    }

    JobSpec build(BuildRequest request) {
        JobSpec.build(
                request.targetImage,
                "build-" + request.buildId.replace('_', '-'),
                request.startTime,
                request.maxDuration,
                request.workDir
        )
    }

    JobSpec scan(ScanRequest request) {
        JobSpec.scan(
                request.scanId,
                "scan-${request.scanId.replaceFirst(/^$ScanRequest.ID_PREFIX/,'')}",
                request.creationTime,
                scanConfig.timeout,
                request.workDir
        )
    }

    JobSpec mirror(MirrorRequest request) {
        JobSpec.mirror(
                request.targetImage,
                "mirror-${request.mirrorId.replaceFirst(/^${MirrorRequest.ID_PREFIX}/,'')}",
                request.creationTime,
                mirrorConfig.maxDuration,
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
