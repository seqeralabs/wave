package io.seqera.wave.stats

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.event.ApplicationEventListener
import io.seqera.wave.service.builder.BuildEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton


/**
 * A listener to model events as statistics data
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@Slf4j
@Singleton
@CompileStatic
class StatsService implements ApplicationEventListener<BuildEvent> {

    @Inject
    Storage storage

    @Override
    void onApplicationEvent(BuildEvent event) {
        storage.addBuild(fromEvent(event))
    }

    static BuildRecord fromEvent(BuildEvent event) {
        return new BuildRecord(
                buildId: event.buildRequest.id,
                dockerFile:event.buildRequest.dockerFile?.replaceAll("[\ud83c\udf00-\ud83d\ude4f]|[\ud83d\ude80-\ud83d\udeff]", ""),
                condaFile:event.buildRequest.condaFile?.replaceAll("[\ud83c\udf00-\ud83d\ude4f]|[\ud83d\ude80-\ud83d\udeff]", ""),
                targetImage:event.buildRequest.targetImage,
                userName:event.buildRequest.user?.userName,
                userEmail:event.buildRequest.user?.email,
                userId:event.buildRequest.user?.id,
                ip:event.buildRequest.ip,
                startTime:event.buildRequest.startTime,
                duration:event.buildResult.duration,
                exitStatus:event.buildResult.exitStatus,
        )
    }
}
