/*
 * Copyright 2023, Seqera Labs
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

/**
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerInspectResponseEx extends ContainerInspectResponse  {

    public ContainerInspectResponseEx(ContainerInspectResponse response) {
        super(new ContainerSpecEx(response.getContainer()));
    }

    public ContainerInspectResponseEx(ContainerSpec spec) {
        super(new ContainerSpecEx(spec));
    }
}
