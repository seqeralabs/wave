package io.seqera.wave.service.build

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerBuildService {

    /**
     * Build a container image for the given dockerfile
     *
     * @param dockerfileContent
     *      The dockerfile encoded as base64 string of the container to build
     * @return
     *      The container image where the resulting image is going to be hosted
     */
    String buildImage(String dockerfileContent)


    BuildStatus waitImageBuild(String targetImage)

}
