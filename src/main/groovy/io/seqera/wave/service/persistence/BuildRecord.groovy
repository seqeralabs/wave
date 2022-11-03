package io.seqera.wave.service.persistence

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import io.seqera.wave.model.BuildEvent

/**
 * A collection of request and response properties to be stored
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ToString
@CompileStatic
@EqualsAndHashCode
class BuildRecord {

    String buildId
    String dockerFile
    String condaFile
    String targetImage
    String userName
    String userEmail
    Long userId
    String requestIp
    Instant startTime
    String offsetId
    Duration duration
    int exitStatus
    String platform

    static BuildRecord fromEvent(BuildEvent event) {
        if( event.request.id != event.result.id )
            throw new IllegalStateException("Build request Id must match result id")
        return new BuildRecord(
                buildId: event.request.id,
                // note: the string replacement is needed to a bug in the SurrealDb version 1.0.0-beta.8
                // see https://pullanswer.com/questions/bug-unicode-escaped-characters-with-surrogate-pairs-causes-surrealdb-to-panic
                dockerFile: event.request.dockerFile?.replaceAll("[\ud83c\udf00-\ud83d\ude4f]|[\ud83d\ude80-\ud83d\udeff]", ""),
                condaFile: event.request.condaFile?.replaceAll("[\ud83c\udf00-\ud83d\ude4f]|[\ud83d\ude80-\ud83d\udeff]", ""),
                targetImage: event.request.targetImage,
                userName: event.request.user?.userName,
                userEmail: event.request.user?.email,
                userId: event.request.user?.id,
                requestIp: event.request.ip,
                startTime: event.request.startTime,
                duration: event.result.duration,
                exitStatus: event.result.exitStatus,
                platform: event.request.platform,
                offsetId: event.request.offsetId
        )
    }
}
