/*
 * Copyright 2025, Seqera Labs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package io.seqera.service.pairing

import java.time.Duration

/**
 * Configuration interface for pairing library settings.
 * Implementations should provide the actual configuration values,
 * typically via Micronaut's @Value or @ConfigurationProperties.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface PairingConfig {

    /**
     * The period of time after which the pairing key should be renewed.
     * Default: 1 day
     */
    Duration getKeyLease()

    /**
     * How long the pairing key stays in the cache store before eviction.
     * Default: 30 days
     */
    Duration getKeyDuration()

    /**
     * Timeout for inbound message futures in the pairing channel.
     * Default: 5 seconds
     */
    Duration getChannelTimeout()

    /**
     * Poll interval for queue consumption in the pairing channel.
     * Default: 100 milliseconds
     */
    Duration getChannelAwaitTimeout()

    /**
     * Whether to close WebSocket session when license token validation fails.
     * Default: false
     */
    boolean getCloseSessionOnInvalidLicenseToken()

    /**
     * List of hosts that are denied from pairing.
     * Default: empty list
     */
    List<String> getDenyHosts()
}
