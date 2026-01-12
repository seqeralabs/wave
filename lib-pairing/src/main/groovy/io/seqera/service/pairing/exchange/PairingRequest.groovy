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

package io.seqera.service.pairing.exchange

import groovy.transform.CompileStatic
import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull

/**
 * Model the request for a remote service instance to register
 * itself as Wave credentials provider
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@CompileStatic
@Introspected
class PairingRequest {

    @NotBlank
    @NotNull
    String service

    @NotBlank
    @NotNull
    String endpoint

    PairingRequest(String service=null, String endpoint=null) {
        this.service = service
        this.endpoint = endpoint
    }
}
