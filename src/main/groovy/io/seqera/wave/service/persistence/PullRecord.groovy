package io.seqera.wave.service.persistence

import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.wave.model.BuildEvent
import io.seqera.wave.model.PullEvent


/**
 * A pull request to be stored
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ToString
@CompileStatic
@EqualsAndHashCode
class PullRecord {

    String userId
    String image
    Instant when
    String action

    static PullRecord fromEvent(PullEvent event) {
        return new PullRecord(
                userId: event.userId,
                image: event.image,
                when: Instant.now(),
                action: "pull"
        )
    }
}
