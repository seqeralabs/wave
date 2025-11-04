/*
 * Copyright 2023-2025, Seqera Labs
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

package io.seqera.wave.cli.model;

import io.seqera.wave.api.ContainerInspectResponse;
import io.seqera.wave.core.spec.ContainerSpec;
import io.seqera.wave.core.spec.IndexSpec;
import io.seqera.wave.model.ContainerOrIndexSpec;

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerInspectResponseEx extends ContainerInspectResponse  {

    public ContainerInspectResponseEx(ContainerInspectResponse response) {
        // Convert to ContainerOrIndexSpec based on what's present
        super(toContainerOrIndexSpec(response));
    }

    public ContainerInspectResponseEx(ContainerSpec spec) {
        super(new ContainerSpecEx(spec));
    }

    public ContainerInspectResponseEx(IndexSpec index) {
        super(index);
    }

    private static ContainerOrIndexSpec toContainerOrIndexSpec(ContainerInspectResponse response) {
        if( response.getContainer() != null ) {
            return new ContainerOrIndexSpec(new ContainerSpecEx(response.getContainer()));
        }
        else if( response.getIndex() != null ) {
            return new ContainerOrIndexSpec(response.getIndex());
        }
        else {
            throw new IllegalArgumentException("Container inspect response contains neither container nor index");
        }
    }
}
