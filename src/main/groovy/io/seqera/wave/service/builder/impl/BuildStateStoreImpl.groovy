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

package io.seqera.wave.service.builder.impl

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.service.builder.BuildResult
import io.seqera.wave.service.builder.BuildEntry
import io.seqera.wave.service.builder.BuildStateStore
import io.seqera.wave.store.state.AbstractStateStore
import io.seqera.wave.store.state.impl.StateProvider
import jakarta.inject.Named
import jakarta.inject.Singleton
/**
 * Implements Cache store for {@link BuildEntry}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class BuildStateStoreImpl extends AbstractStateStore<BuildEntry> implements BuildStateStore {

    private BuildConfig buildConfig

    private ExecutorService ioExecutor

    BuildStateStoreImpl(StateProvider<String, String> provider, BuildConfig buildConfig, @Named(TaskExecutors.IO) ExecutorService ioExecutor) {
        super(provider, new MoshiEncodeStrategy<BuildEntry>() {})
        this.buildConfig = buildConfig
        this.ioExecutor = ioExecutor
    }

    @Override
    protected String getPrefix() {
        return 'wave-build/v2:'
    }

    @Override
    protected Duration getDuration() {
        return buildConfig.statusDuration
    }

    @Override
    BuildEntry getBuild(String imageName) {
        return get(imageName)
    }

    @Override
    BuildResult getBuildResult(String imageName) {
        return getBuild(imageName)?.getResult()
    }

    @Override
    void storeBuild(String imageName, BuildEntry buildStoreEntry) {
        put(imageName, buildStoreEntry)
    }

    @Override
    void storeBuild(String imageName, BuildEntry result, Duration ttl) {
        put(imageName, result, ttl)
    }

    @Override
    boolean storeIfAbsent(String imageName, BuildEntry build) {
        return putIfAbsent(imageName, build, buildConfig.statusDuration)
    }

    @Override
    void removeBuild(String imageName) {
        remove(imageName)
    }

    @Override
    CompletableFuture<BuildResult> awaitBuild(String imageName) {
        final result = getBuildResult(imageName)
        if( !result )
            return null
        return CompletableFuture<BuildResult>.supplyAsync(() -> Waiter.awaitCompletion(this, imageName, result), ioExecutor)
    }

    /**
     * Implement waiter common logic
     */
    private static class Waiter {

        static BuildResult awaitCompletion(BuildStateStoreImpl store, String imageName, BuildResult current) {
            final await = store.buildConfig.statusDelay
            while( true ) {
                if( current==null ) {
                    return BuildResult.unknown()
                }

                // check is completed
                if( current.done() ) {
                    return current
                }
                // sleep a bit
                Thread.sleep(await.toMillis())
                // fetch the build status again
                current = store.getBuildResult(imageName)
            }
        }
    }

    /**
     * Load a build entry via the record id
     *
     * @param requestId The ID of the record to be loaded
     * @return The {@link BuildEntry} with with corresponding Id of {@code null} if it cannot be found
     */
    @Override
    BuildEntry findByRequestId(String requestId) {
        super.findByRequestId(requestId)
    }
}
