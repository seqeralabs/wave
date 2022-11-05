package io.seqera.wave.service.cache.impl

import io.seqera.wave.service.cache.CacheStore

/**
 * Define an cache interface alias to be used by cache implementation providers
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface CacheProvider<K,V> extends CacheStore<K,V> {
}
