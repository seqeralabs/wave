package io.seqera.wave.service.data.queue.impl

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Prototype
import io.micronaut.context.annotation.Requires
import io.seqera.wave.exception.BadRequestException
import io.seqera.wave.service.data.queue.ConsumerStore
import io.seqera.wave.service.data.queue.QueueBroker

@Slf4j
@Requires(notEnv='redis')
@Prototype
@CompileStatic
class LocalQueueBroker implements QueueBroker<String> {

    private ConsumerStore<String> localConsumers

    @Override
    init(ConsumerStore<String> localConsumers) {
        this.localConsumers = localConsumers
    }

    @Override
    def send(String queueKey, String message) {
        if (!localConsumers.canConsume(queueKey))
            throw new BadRequestException("No consumers at '${queueKey}'")

        localConsumers.consume(queueKey, message)
    }
}
