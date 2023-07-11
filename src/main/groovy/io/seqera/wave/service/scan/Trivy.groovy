package io.seqera.wave.service.scan

/**
 * Trivy constants
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface Trivy {

    static final public String CACHE_MOUNT_PATH = '/root/.cache/'

    static final public String CONFIG_MOUNT_PATH = '/root/.docker/config.json'

}
