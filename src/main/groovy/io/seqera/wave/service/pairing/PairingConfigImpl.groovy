/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2025, Seqera Labs
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

package io.seqera.wave.service.pairing

import io.seqera.service.pairing.PairingConfig

import java.time.Duration
import jakarta.annotation.PostConstruct

import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton

/**
 * Implementation of PairingConfig that provides configuration values
 * via Micronaut's @Value injection.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@Slf4j
@Singleton
@CompileStatic
class PairingConfigImpl implements PairingConfig {

    @Value('${wave.pairing-key.lease:`1d`}')
    private Duration keyLease

    @Value('${wave.pairing-key.duration:`30d`}')
    private Duration keyDuration

    @Value('${wave.pairing.channel.timeout:5s}')
    private Duration channelTimeout

    @Value('${wave.pairing.channel.awaitTimeout:100ms}')
    private Duration channelAwaitTimeout

    @Value('${wave.closeSessionOnInvalidLicenseToken:false}')
    private boolean closeSessionOnInvalidLicenseToken

    @Nullable
    @Value('${wave.denyHosts}')
    private List<String> denyHosts

    @PostConstruct
    private void init() {
        log.info "Pairing configuration - keyLease=${keyLease}; keyDuration=${keyDuration}; channelTimeout=${channelTimeout}; channelAwaitTimeout=${channelAwaitTimeout}; closeSessionOnInvalidLicenseToken=${closeSessionOnInvalidLicenseToken}; denyHosts=${denyHosts}"
    }

    @Override
    Duration getKeyLease() {
        return keyLease
    }

    @Override
    Duration getKeyDuration() {
        return keyDuration
    }

    @Override
    Duration getChannelTimeout() {
        return channelTimeout
    }

    @Override
    Duration getChannelAwaitTimeout() {
        return channelAwaitTimeout
    }

    @Override
    boolean getCloseSessionOnInvalidLicenseToken() {
        return closeSessionOnInvalidLicenseToken
    }

    @Override
    List<String> getDenyHosts() {
        return denyHosts ?: List.of()
    }
}
