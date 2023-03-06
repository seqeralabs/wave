package io.seqera.wave.service.data.impl

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.FutureListener
import io.seqera.wave.service.data.FuturePublisher

/**
 * Implement a publish for a single instance mode. This is meant to be used
 * for developing purposes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Requires(notEnv='redis')
@Prototype
@CompileStatic
class LocalFuturePublisher implements FuturePublisher<String> {

    private FutureListener<String> listener

    @Override
    void subscribe(FutureListener<String> listener) {
        this.listener = listener
    }

    @Override
    void publish(String entry) {
        listener.receive(entry)
    }
}
