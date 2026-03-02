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

package io.seqera.wave.service.builder

import java.time.Duration
import java.time.Instant

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.random.LongRndKey
import io.seqera.wave.tower.PlatformId
/**
 * Model a multi-platform build request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includeNames = true, includePackage = false)
@Canonical
@CompileStatic
class MultiBuildRequest {

    static final String ID_PREFIX = 'mb-'

    final String multiBuildId
    final String targetImage
    final String containerId
    final String buildId
    final String amd64TargetImage
    final String arm64TargetImage
    final boolean amd64Cached
    final boolean arm64Cached
    final PlatformId identity
    final Instant creationTime
    final Duration maxDuration

    static MultiBuildRequest create(
            String containerId,
            String targetImage,
            String buildId,
            String amd64TargetImage,
            String arm64TargetImage,
            boolean amd64Cached,
            boolean arm64Cached,
            PlatformId identity,
            Duration maxDuration
    ) {
        assert targetImage, "Argument 'targetImage' cannot be empty"
        assert buildId, "Argument 'buildId' cannot be empty"

        final multiBuildId = ID_PREFIX + LongRndKey.rndHex()
        return new MultiBuildRequest(
                multiBuildId,
                targetImage,
                containerId,
                buildId,
                amd64TargetImage,
                arm64TargetImage,
                amd64Cached,
                arm64Cached,
                identity,
                Instant.now(),
                maxDuration
        )
    }

    static MultiBuildRequest of(Map opts) {
        new MultiBuildRequest(
                opts.multiBuildId as String,
                opts.targetImage as String,
                opts.containerId as String,
                opts.buildId as String,
                opts.amd64TargetImage as String,
                opts.arm64TargetImage as String,
                opts.amd64Cached as boolean,
                opts.arm64Cached as boolean,
                opts.identity as PlatformId,
                opts.creationTime as Instant,
                opts.maxDuration as Duration
        )
    }
}
