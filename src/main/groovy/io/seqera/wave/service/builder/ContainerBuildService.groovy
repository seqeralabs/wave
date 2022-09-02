package io.seqera.wave.service.builder

import java.util.concurrent.CompletableFuture

import io.seqera.wave.core.RoutePath
/**
 * Declare container build service interface
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface ContainerBuildService {

    /**
     * Build a container image for the given {@link BuildRequest}
     *
     * @param request
     *      A {@link BuildRequest} modelling the build request
     * @return
     *      The container image where the resulting image is going to be hosted
     */
    String buildImage(BuildRequest request)

    /**
     * Get a completable future that holds the build result
     *
     * @param targetImage
     *      the container repository name where the target image is expected to be retrieved once the
     *      build it complete
     * @return
     *      A completable future that holds the resulting {@link BuildResult} or
     *      {@code null} if not request has been submitted for such image
     */
    CompletableFuture<BuildResult> buildResult(String targetImage)

    /**
     * Get a completable future that holds the build result
     *
     * @param route
     *      A {@link RoutePath} instance representing the container request
     * @return
     *      A completable future that holds the resulting {@link BuildResult} or
     *      {@code null} if not request has been submitted for such image
     */
    default CompletableFuture<BuildResult> buildResult(RoutePath route) {
        return route.request?.containerImage
                ? buildResult(route.request.containerImage)
                : null
    }

}
