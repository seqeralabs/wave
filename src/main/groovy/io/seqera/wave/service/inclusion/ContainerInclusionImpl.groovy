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

package io.seqera.wave.service.inclusion

import groovy.transform.CompileStatic
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.spec.ObjectRef
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.tower.PlatformId
import jakarta.inject.Inject
import jakarta.inject.Singleton
/**
 * Implement the container inclusion service which takes care of expanding
 * a list of container names into a set of layers to the added to the target request
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Singleton
@CompileStatic
class ContainerInclusionImpl implements ContainerInclusionService {

    @Inject
    private ContainerInspectService inspectService

    @Override
    SubmitContainerTokenRequest addContainerInclusions(SubmitContainerTokenRequest request, PlatformId identity) {
        final containerNames = request.containerIncludes
        if( !containerNames )
            return request
        if( containerNames.size()>10 )
            throw new BadRequestException("The number of container inclusions cannot be greater than 10")

        final result = new ArrayList<ContainerLayer>()
        for( String it : containerNames ) {
            // submit a container inspect request to find out the layers making up the contaner
            final spec = inspectService.containerSpec(it, identity)
            final List<ObjectRef> layerRef = spec.getManifest().getLayers();
            // add each entry as a new container layer in the request container config
            for( int i=0; i<layerRef.size(); i++ ) {
                final ObjectRef ref = layerRef.get(i);
                final String tarDigest = spec.config.rootfs.diff_ids.get(i);
                // compose the layer uri - the 'docker://' is a pseudo protocol used by wave to handle this use case
                final String location = "docker://$spec.registry/v2/$spec.imageName/blobs/$ref.digest"
                final ContainerLayer layer = new ContainerLayer(
                        location,
                        ref.digest,
                        ref.size.intValue(),
                        tarDigest )
                result.add(layer)
            }
        }
        // update the config object in the request
        final config = request.containerConfig ?: new ContainerConfig()
        config.layers.addAll(result)
        return request.withContainerConfig(config)
    }

}
