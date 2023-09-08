/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
 */

package io.seqera.wave.controller

import javax.annotation.Nullable
import javax.validation.constraints.NotBlank

import io.micronaut.core.annotation.Introspected

@Introspected
class ValidateRegistryCredsRequest {
    @NotBlank
    String userName
    @NotBlank
    String password
    @NotBlank
    String registry
}
