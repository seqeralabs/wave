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

import java.util.List;
import java.util.Objects;

import io.seqera.wave.config.CondaOpts;
import io.seqera.wave.config.CranOpts;

/**
 * Model a Package environment requirements
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class PackagesSpec {

    public enum Type { CONDA, CRAN }

    public Type type;

    /**
     * The package environment file encoded as a base64 string. When this is provided the field {@link #entries} is not allowed
     */
    public String environment;

    /**
     * A list of one or more packages. When this is provided the field {@link #environment} is not allowed
     */
    public List<String> entries;

    /**
     * Conda build options
     */
    public CondaOpts condaOpts;

    /**
     * CRAN build options
     */
    public CranOpts cranOpts;

    /**
     * channels used for downloading packages
     */
    public List<String> channels;

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        PackagesSpec that = (PackagesSpec) object;
        return type == that.type
                && Objects.equals(environment, that.environment)
                && Objects.equals(entries, that.entries)
                && Objects.equals(condaOpts, that.condaOpts)
                && Objects.equals(cranOpts, that.cranOpts)
                && Objects.equals(channels, that.channels);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, environment, entries, condaOpts, cranOpts, channels);
    }

    @Override
    public String toString() {
        return "PackagesSpec{" +
                "type=" + type +
                ", envFile='" + environment + '\'' +
                ", packages=" + entries +
                ", condaOpts=" + condaOpts +
                ", cranOpts=" + cranOpts +
                ", channels=" + ObjectUtils.toString(channels) +
                '}';
    }

    public PackagesSpec withType(Type type) {
        this.type = type;
        return this;
    }

    public PackagesSpec withEnvironment(String encoded) {
        this.environment = encoded;
        return this;
    }

    public PackagesSpec withEntries(List<String> entries) {
        this.entries = entries;
        return this;
    }

    public PackagesSpec withChannels(List<String> channels) {
        this.channels = channels;
        return this;
    }

    public PackagesSpec withCondaOpts(CondaOpts opts) {
        this.condaOpts = opts;
        return this;
    }

    public PackagesSpec withCranOpts(CranOpts opts) {
        this.cranOpts = opts;
        return this;
    }

}
