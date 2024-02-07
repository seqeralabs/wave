package io.seqera.wave.service.inclusion

import groovy.transform.CompileStatic
import io.seqera.wave.api.ContainerConfig
import io.seqera.wave.api.ContainerLayer
import io.seqera.wave.api.SubmitContainerTokenRequest
import io.seqera.wave.core.spec.ObjectRef
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.inspect.ContainerInspectService
import io.seqera.wave.tower.User
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
    SubmitContainerTokenRequest addContainerInclusions(SubmitContainerTokenRequest request, User user) {
        final containerNames = request.containerIncludes
        if( !containerNames )
            return request
        if( containerNames.size()>10 )
            throw new BadRequestException("The number of container inclusions cannot be greater than 10")

        final result = new ArrayList<ContainerLayer>()
        for( String it : containerNames ) {
            // submit a container inspect request to find out the layers making up the contaner
            final spec = inspectService.containerSpec(it, user?.id, request.towerWorkspaceId, request.towerAccessToken, request.towerEndpoint)
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
