package io.seqera.wave.service.builder

import java.util.concurrent.CompletableFuture
import javax.annotation.Nullable

import io.seqera.wave.core.RoutePath
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
    String buildImage(String dockerfileContent, @Nullable String condaFile, @Nullable User user)

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
