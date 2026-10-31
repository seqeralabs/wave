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

import java.util.concurrent.CompletableFuture

import io.seqera.wave.core.ContainerPlatform
import io.seqera.wave.tower.PlatformId
/**
 * Build and look up the SOCI zTOCs of container image layers
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface SociIndexService {

    /**
     * Find the zTOCs available for the given layers
     *
     * @param layerDigests The layer digests
     * @return The zTOCs found, by layer digest. Layers not indexed (yet) are missing from the map
     */
    Map<String,ZtocEntry> findZtocs(Collection<String> layerDigests)

    /**
     * Launch the indexing of the eligible layers of an image manifest that have no zTOC yet
     *
     * @param repository The image repository including the registry e.g. {@code docker.io/library/ubuntu}
     * @param manifestJson The image manifest
     * @param identity The identity of the requester, used to access private repositories
     */
    void indexLayers(String repository, String manifestJson, PlatformId identity)

    /**
     * Launch, in the background, the indexing of a container image for the given platform
     *
     * @param containerImage The container image e.g. {@code docker.io/library/ubuntu:24.04}
     * @param platform The target platform
     * @param identity The identity of the requester
     */
    CompletableFuture<Void> requestIndex(String containerImage, ContainerPlatform platform, PlatformId identity)
}
