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

package io.seqera.wave.service.mirror

import java.util.concurrent.CompletableFuture

import io.seqera.wave.service.builder.BuildTrack

/**
 * Define the contract for container images mirroring service
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerMirrorService {

    /**
     * Submit a container mirror request
     *
     * @param request
     *      The {@link MirrorRequest} modelling the container mirror request
     * @return
     *      A {@link BuildTrack} object representing the state of the request
     */
    BuildTrack mirrorImage(MirrorRequest request)

    /**
     * Await the completion for the specified target container image
     *
     * @param targetImage
     *      The container image of the mirror operation to be awaited
     * @return
     *      A future holding the {@link MirrorEntry} when the mirror operation complete
     */
    CompletableFuture<MirrorEntry> awaitCompletion(String targetImage)

    /**
     * Retrieve the current state of the mirror operation
     *
     * @param id
     *      The id of the mirror state record
     * @return
     *      The {@link MirrorEntry} object modelling the current state of the mirror operation,
     *      or {@link null} otherwise
     */
    MirrorResult getMirrorResult(String id)

}
