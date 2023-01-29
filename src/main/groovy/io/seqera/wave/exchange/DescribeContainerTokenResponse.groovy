package io.seqera.wave.exchange

import groovy.transform.Canonical
import groovy.transform.CompileStatic
import io.seqera.wave.service.persistence.WaveContainerRecord

/**
 * Model a container request record
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Canonical
@CompileStatic
class DescribeContainerTokenResponse {

    String token
    
    WaveContainerRecord request

}
