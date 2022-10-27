package io.seqera.wave.service.persistence

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.ToString
import io.seqera.wave.service.builder.BuildEvent


/**
 * A collection of request and response properties to be stored
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ToString
@CompileStatic
class BuildRecord {

    String buildId
    String dockerFile
    String condaFile
    String targetImage
    String userName
    String userEmail
    Long userId
    String ip
    Instant startTime
    Duration duration
    int exitStatus

    static BuildRecord fromEvent(BuildEvent event) {
        return new BuildRecord(
                buildId: event.buildRequest.id,
                // note: the string replacement is needed to a bug in the SurrealDb version 1.0.0-beta.8
                // see https://pullanswer.com/questions/bug-unicode-escaped-characters-with-surrogate-pairs-causes-surrealdb-to-panic
                dockerFile: event.buildRequest.dockerFile?.replaceAll("[\ud83c\udf00-\ud83d\ude4f]|[\ud83d\ude80-\ud83d\udeff]", ""),
                condaFile: event.buildRequest.condaFile?.replaceAll("[\ud83c\udf00-\ud83d\ude4f]|[\ud83d\ude80-\ud83d\udeff]", ""),
                targetImage: event.buildRequest.targetImage,
                userName: event.buildRequest.user?.userName,
                userEmail: event.buildRequest.user?.email,
                userId: event.buildRequest.user?.id,
                ip: event.buildRequest.ip,
                startTime: event.buildRequest.startTime,
                duration: event.buildResult.duration,
                exitStatus: event.buildResult.exitStatus,
        )
    }
}
