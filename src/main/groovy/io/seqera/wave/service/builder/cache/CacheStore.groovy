package io.seqera.wave.service.builder.cache

import java.util.concurrent.CompletableFuture

import io.seqera.wave.service.builder.BuildRequest

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CacheStore {

    boolean containsKey(String key)

    BuildRequest get(String key)

    void put(String key, BuildRequest value)

    CompletableFuture<BuildRequest> await(String key)

}
