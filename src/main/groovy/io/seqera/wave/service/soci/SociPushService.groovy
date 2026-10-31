/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2026, Seqera Labs
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

package io.seqera.wave.service.soci

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
/**
 * Publish the SOCI-enabled tag {@code <tag>-soci} of durable (freeze and mirror) containers, which
 * are pulled from their registry directly and therefore cannot be altered by the Wave proxy
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface SociPushService {

    /**
     * The image to be returned for a durable container requested with SOCI. The SOCI-enabled tag is
     * returned only when it already exists, otherwise its publication is started and the given image
     * is returned, so that a pull never hits a missing tag
     *
     * @param targetImage The container image e.g. {@code quay.io/user/app:1.0}
     * @param platform The container platform
     * @param identity The identity of the requester
     * @return The SOCI-enabled image e.g. {@code quay.io/user/app:1.0-soci}, or {@code targetImage}
     */
    String sociTarget(String targetImage, ContainerPlatform platform, PlatformId identity)
}
