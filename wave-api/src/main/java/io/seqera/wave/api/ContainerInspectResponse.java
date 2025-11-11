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

package io.seqera.wave.api;

import java.util.Objects;

import io.seqera.wave.core.spec.ContainerSpec;
import io.seqera.wave.core.spec.IndexSpec;
import io.seqera.wave.model.ContainerOrIndexSpec;

/**
 * Model a container inspect request response
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class ContainerInspectResponse {

    /**
     * The spec of the specific container or {@code null} when a multi-architecture container was specified.
     * See also {@link #index}.
     */
    ContainerSpec container;

    /**
     * The index spec of a multi-architecture container. This attribute is mutually exclusive with {@link #container}.
     */
    IndexSpec index;

    private ContainerInspectResponse() {

    }

    public ContainerInspectResponse(ContainerSpec container) {
        this.container = container;
    }

    public ContainerInspectResponse(IndexSpec index) {
        this.index = index;
    }

    public ContainerInspectResponse(ContainerOrIndexSpec spec) {
        this.container = spec.getContainer();
        this.index = spec.getIndex();
    }

    public ContainerSpec getContainer() {
        return container;
    }

    public IndexSpec getIndex() {
        return index;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        ContainerInspectResponse that = (ContainerInspectResponse) object;
        return Objects.equals(container, that.container)
                && Objects.equals(index, that.index)
                ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(container, index);
    }

    @Override
    public String toString() {
        return "ContainerInspectResponse{" +
                "container=" + container +
                "index=" + index +
                '}';
    }
}
