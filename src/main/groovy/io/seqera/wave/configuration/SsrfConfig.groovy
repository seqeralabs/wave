/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2026, Seqera Labs
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

import groovy.transform.CompileStatic
import io.micronaut.context.annotation.Value
import io.micronaut.core.annotation.Nullable
import jakarta.inject.Singleton

/**
 * Configuration for SSRF protection
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */
@CompileStatic
@Singleton
class SsrfConfig {

    @Value('${wave.security.ssrf-protection.enabled:false}')
    Boolean ssrfProtectionEnabled

    /**
     * Registries that anonymous (unauthenticated) requests are allowed to reference.
     * Entries are hostnames, optionally with a {@code *.} wildcard prefix e.g. {@code *.amazonaws.com}.
     * When unset no allowlist restriction is applied; authenticated requests are never restricted.
     * Only effective when {@code wave.security.ssrf-protection.enabled} is {@code true}.
     */
    @Nullable
    @Value('${wave.security.ssrf-protection.registry-allowlist}')
    List<String> registryAllowlist

}
