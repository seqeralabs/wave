package io.seqera.wave.service.data.future

import java.time.Duration
/**
 * Define the interface for a future queue communication channel
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FutureQueue<V> {

    void offer(String key, V value, Duration expiration)

    V poll(String key)

}
