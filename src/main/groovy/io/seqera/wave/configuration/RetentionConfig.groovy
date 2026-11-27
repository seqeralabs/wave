/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.configuration

import java.time.Duration

import groovy.transform.CompileStatic
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Value
import jakarta.annotation.PostConstruct
/**
 * Model the data retention config options
 *
 * Periods are validated even when retention is disabled, so a typo in a deployment that means
 * to switch it on later fails at startup rather than on the day it is turned on.
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@Slf4j
@Context
@CompileStatic
@ToString(includeNames = true, includePackage = false)
class RetentionConfig {

    @Value('${wave.retention.enabled:false}')
    boolean enabled

    @Value('${wave.retention.build:90d}')
    Duration build

    @Value('${wave.retention.request:90d}')
    Duration request

    @Value('${wave.retention.mirror:90d}')
    Duration mirror

    @Value('${wave.retention.scan:5d}')
    Duration scan

    @Value('${wave.retention.interval:1h}')
    Duration interval

    // bounds the work a single batch does against the database and the object storage
    @Value('${wave.retention.batch-size:1000}')
    int batchSize

    @PostConstruct
    private void init() {
        validate('wave.retention.build', build)
        validate('wave.retention.request', request)
        validate('wave.retention.mirror', mirror)
        validate('wave.retention.scan', scan)
        validate('wave.retention.interval', interval)
        if( batchSize <= 0 )
            throw new IllegalArgumentException("Config setting 'wave.retention.batch-size' must be greater than zero - offending value: ${batchSize}")
        if( enabled )
            log.info "Data retention enabled - ${this}"
        else
            log.debug "Data retention disabled - no record is removed"
    }

    static private void validate(String name, Duration value) {
        if( value==null || value.toMillis() <= 0 )
            throw new IllegalArgumentException("Config setting '${name}' must be a duration greater than zero - offending value: ${value}")
    }
}
