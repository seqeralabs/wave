package io.seqera.wave.service.builder

import io.seqera.wave.tower.User

/**
 * Declare container build service interface
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerBuildService {

    /**
     * Build a container image for the given dockerfile
     *
     * @param dockerfileContent
     *      The dockerfile encoded as base64 string of the container to build
     * @param condaFile
     *      A conda recipe file that can be associated this image build
     * @return
     *      The container image where the resulting image is going to be hosted
     */
    String buildImage(String dockerfileContent, String condaFile, User user)

    BuildStatus isUnderConstruction( String targetImage )

}
