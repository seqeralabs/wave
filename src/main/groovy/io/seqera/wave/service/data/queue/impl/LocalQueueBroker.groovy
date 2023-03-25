package io.seqera.wave.service.data.queue.impl

import java.util.function.Consumer

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.seqera.wave.service.data.queue.ConsumerTopic
import io.seqera.wave.service.data.queue.QueueBroker

/**
 * Local queue broker that just forwards all incoming messages to
 * a single local consumer
 *
 * @author Jordi Deu-Pons <jordi@seqera.io>
 */
@Slf4j
@Requires(notEnv='redis')
@Prototype
@CompileStatic
class LocalQueueBroker implements QueueBroker<String> {

    private ConsumerTopic<String> localConsumers

    @Override
    void init(ConsumerTopic<String> localConsumers) {
        this.localConsumers = localConsumers
    }

    @Override
    void send(String queueKey, String message) {
        if (!localConsumers.canConsume(queueKey)) {
            log.debug "No local consumers for queue=$queueKey"
            return
        }

        localConsumers.consume(queueKey, message)
    }

    @Override
    void consume(String queueKey, Consumer<String> consumer) {
    }
}
