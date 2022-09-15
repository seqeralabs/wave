package io.seqera.wave.service.builder

import java.util.concurrent.CompletableFuture

import io.seqera.wave.service.builder.BuildRequest

/**
 * Define build store operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface BuildStore {

    /**
     *
     * @param imageName
     * @return
     */
    boolean hasBuild(String imageName)

    BuildRequest getBuild(String imageName)

    void storeBuild(String imageName, BuildRequest request)

    CompletableFuture<BuildRequest> awaitBuild(String imageName)

}
