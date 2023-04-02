package io.seqera.wave.service.data.future

import java.time.Duration
import java.util.concurrent.TimeoutException

/**
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FutureQueue<V> {

    void offer(String key, V value)

    V poll(String key, Duration timeout) throws TimeoutException

    default close() { }
}
