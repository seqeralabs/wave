package io.seqera.wave.service.data.future
/**
 * Define the interface for a future queue communication channel
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FutureQueue<V> {

    void offer(String key, V value)

    V poll(String key)

}
