package io.seqera.wave.service.builder

import java.time.Duration
import java.util.concurrent.CompletableFuture

import groovy.transform.CompileStatic
import io.seqera.wave.exception.BuildTimeoutException
/**
 * Define build request store operations
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Deprecated
@CompileStatic
interface BuildStore {

    Duration getTimeout()

    Duration getDelay()

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
    void storeBuild(String imageName, BuildResult result)

    /**
     * Store a container image build request using the specified time-to-live duration
     *
     * @param imageName The container image name
     * @param result The {@link BuildResult} object associated to the image name
     * @param ttl The {@link Duration} after which the entry is expired
     */
    void storeBuild(String imageName, BuildResult result, Duration ttl)

    /**
     * Store a build result only if the specified key does not exit
     *
     * @param imageName The container image unique key
     * @param build The {@link BuildResult} desired status to be stored
     * @return {@code true} if the {@link BuildResult} was stored, {@code false} otherwise
     */
    boolean storeIfAbsent(String imageName, BuildResult build)

    /**
     * Remove the build status for the given image name
     *
     * @param imageName
     */
    void removeBuild(String imageName)

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
    default CompletableFuture<BuildResult> awaitBuild(String imageName) {
        final result = getBuild(imageName)
        if( !result )
            return null
        return CompletableFuture<BuildResult>.supplyAsync(() -> Waiter.awaitCompletion(this,imageName,result))
    }

    /**
     * Implement waiter common logic
     */
    private static class Waiter {

        static BuildResult awaitCompletion(BuildStore store, String imageName, BuildResult current) {
            final beg = System.currentTimeMillis()
            // add 10% delay gap to prevent race condition with timeout expiration
            final max = (store.timeout.toMillis() * 1.10) as long
            while( true ) {
                if( current==null ) {
                    return BuildResult.unknown()
                }

                // check is completed
                if( current.done() ) {
                    return current
                }
                // check if it's timed out
                final delta = System.currentTimeMillis()-beg
                if( delta > max )
                    throw new BuildTimeoutException("Build of container '$imageName' timed out")
                // sleep a bit
                Thread.sleep(store.delay.toMillis())
                // fetch the build status again
                current = store.getBuild(imageName)
            }
        }
    }
}
