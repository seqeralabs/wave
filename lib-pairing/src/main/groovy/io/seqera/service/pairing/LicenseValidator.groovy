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

import java.time.Instant

/**
 * Interface for license token validation.
 * Applications using lib-pairing can implement this interface to integrate
 * with their license management system.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface LicenseValidator {

    /**
     * Response object containing license validation result
     */
    static class LicenseCheckResult {
        String id
        Instant expiration

        boolean isExpired() {
            return expiration != null && expiration.isBefore(Instant.now())
        }
    }

    /**
     * Validates a license token for a given product.
     *
     * @param token The license token to validate
     * @param product The product identifier
     * @return {@link LicenseCheckResult} if the token is valid, {@code null} if invalid
     */
    LicenseCheckResult checkToken(String token, String product)
}
