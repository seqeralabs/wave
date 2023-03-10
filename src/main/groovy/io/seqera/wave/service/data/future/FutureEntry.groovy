package io.seqera.wave.service.data.future

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Model a future store entry. This is an internal data structure.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
@PackageScope
class FutureEntry<K,V> {
    final K key
    final V value
}
