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

package io.seqera.wave.service.builder

import java.time.Duration
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.scheduling.TaskExecutors
import io.seqera.wave.configuration.BuildConfig
import io.seqera.wave.encoder.MoshiEncodeStrategy
import io.seqera.wave.exception.BuildTimeoutException
import io.seqera.wave.service.cache.AbstractCacheStore
import io.seqera.wave.service.cache.impl.CacheProvider
import jakarta.inject.Named
import jakarta.inject.Singleton
/**
 * Implements Cache store for {@link BuildResult}
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class BuildCacheStore extends AbstractCacheStore<BuildResult> implements BuildStore {

    private BuildConfig buildConfig

    private ExecutorService ioExecutor

    BuildCacheStore(CacheProvider<String, String> provider, BuildConfig buildConfig, @Named(TaskExecutors.IO) ExecutorService ioExecutor) {
        super(provider, new MoshiEncodeStrategy<BuildResult>() {})
        this.buildConfig = buildConfig
        this.ioExecutor = ioExecutor
    }

    @Override
    protected String getPrefix() {
        return 'wave-build/v1:'
    }

    @Override
    protected Duration getDuration() {
        return buildConfig.statusDuration
    }

    @Override
    Duration getTimeout() {
        return buildConfig.buildTimeout
    }

    @Override
    Duration getDelay() {
        return buildConfig.statusDelay
    }

    @Override
    BuildResult getBuild(String imageName) {
        return get(imageName)
    }

    @Override
    void storeBuild(String imageName, BuildResult result) {
        put(imageName, result)
    }

    @Override
    void storeBuild(String imageName, BuildResult result, Duration ttl) {
        put(imageName, result, ttl)
    }

    @Override
    boolean storeIfAbsent(String imageName, BuildResult build) {
        // store up 1.5 time the build timeout to prevent a missed cache
        // update on job termination remains too long in the store
        final ttl = Duration.ofMillis(Math.round(getTimeout().toMillis() * 1.5f))
        return putIfAbsent(imageName, build, ttl)
    }

    @Override
    void removeBuild(String imageName) {
        remove(imageName)
    }

    @Override
    CompletableFuture<BuildResult> awaitBuild(String imageName) {
        final result = getBuild(imageName)
        if( !result )
            return null
        return CompletableFuture<BuildResult>.supplyAsync(() -> Waiter.awaitCompletion(this,imageName,result), ioExecutor)
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
