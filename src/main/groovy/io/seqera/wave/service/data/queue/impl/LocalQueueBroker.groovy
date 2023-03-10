package io.seqera.wave.service.data.queue.impl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.data.queue.ConsumerGroup
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

    private ConsumerGroup<String> localConsumers

    @Override
    init(ConsumerGroup<String> localConsumers) {
        this.localConsumers = localConsumers
    }

    @Override
    send(String queueKey, String message) {
        if (!localConsumers.canConsume(queueKey))
            throw new BadRequestException("No consumers at '${queueKey}'")

        localConsumers.consume(queueKey, message)
    }
}
