package io.seqera.wave.service.builder

/**
 * Model a container image build status
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
enum BuildStatus {
    UNKNOWN,
    IN_PROGRESS,
    SUCCEED,
    FAILED
}
