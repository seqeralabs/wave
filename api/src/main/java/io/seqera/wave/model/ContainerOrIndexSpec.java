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

package io.seqera.wave.model;

import io.seqera.wave.core.spec.ContainerSpec;
import io.seqera.wave.core.spec.IndexSpec;

/**
 * Model an adapter class to hold either a {@link ContainerSpec} object
 * or a {@link IndexSpec} object.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerOrIndexSpec {

    final ContainerSpec container;
    final IndexSpec index;

    public ContainerOrIndexSpec(ContainerSpec container) {
        this.container = container;
        this.index = null;
    }

    public ContainerOrIndexSpec(IndexSpec index) {
        this.index = index;
        this.container = null;
    }

    ContainerOrIndexSpec(ContainerSpec container, IndexSpec index) {
        this.container = container;
        this.index = index;
    }

    public ContainerSpec getContainer() {
        return container;
    }

    public IndexSpec getIndex() {
        return index;
    }
}
