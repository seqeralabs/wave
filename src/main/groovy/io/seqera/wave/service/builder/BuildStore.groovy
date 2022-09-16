package io.seqera.wave.service.builder

import java.util.concurrent.CompletableFuture
/**
 * Define build request store operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BuildStore {

    /**
     * Check is a build is available
     *
     * @param imageName Container image name to be check
     * @return {@code true} if the store holds the {@link BuildResult} for the specified image name
     */
    boolean hasBuild(String imageName)

    /**
     * Retrieve a container image {@link BuildResult}
     *
     * @param imageName The container image name
     * @return The corresponding {@link BuildResult} or {@code null} otherwise
     */
    BuildResult getBuild(String imageName)

    /**
     * Store a container image build request
     *
     * @param imageName The container image name
     * @param request The {@link BuildResult} object associated to the image name
     */
    void storeBuild(String imageName, BuildResult request)

    /**
     * Await for the container image build completion
     *
     * @param imageName
     *      The target container image name to be build
     * @return
     *      the {@link CompletableFuture} holding the {@BuildResult} associated with
     *      specified image name or {@code null} if no build is associated for the
     *      given image name
     */
    CompletableFuture<BuildResult> awaitBuild(String imageName)

}
