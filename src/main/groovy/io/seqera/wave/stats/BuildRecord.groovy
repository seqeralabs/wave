package io.seqera.wave.stats

import java.time.Duration
import java.time.Instant

import groovy.transform.CompileStatic
import groovy.transform.ToString


/**
 * A collection of request and response properties to be stored
 *
 * @author : jorge <jorge.aguilera@seqera.io>
 *
 */
@ToString
@CompileStatic
class BuildRecord {

    String id
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

}
