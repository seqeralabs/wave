package io.seqera.wave.service.builder.cache
/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CacheStore<String , BuildRequest> {

    boolean containsKey(String key)

    BuildRequest get(String key)

    void put(String key, BuildRequest value)

    BuildRequest await(String key)

}
