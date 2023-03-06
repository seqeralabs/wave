package io.seqera.wave.service.data

/**
 * Define the interface for receiving (listening) value objects
 * over a distributed environment.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface FutureListener<T> {

    /**
     * @return A string representing the name of the group to be listened
     */
    String group()

    /**
     * Method invoked when a message is received over the topic
     * @param message
     */
    void receive(T message)

}
