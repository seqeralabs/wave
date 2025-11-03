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

public class ServiceInfo {

    /** Application version string */
    final public String version;

    /** Build commit ID */
    final public String commitId;

    public ServiceInfo(String version, String commitId) {
        this.version = version;
        this.commitId = commitId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInfo that = (ServiceInfo) o;
        return Objects.equals(version, that.version) && Objects.equals(commitId, that.commitId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(version, commitId);
    }
}


