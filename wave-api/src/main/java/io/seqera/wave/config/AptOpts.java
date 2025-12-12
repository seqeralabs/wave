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

package io.seqera.wave.config;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * APT/Debian build options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class AptOpts {
    public static final String DEFAULT_BASE_IMAGE = "ubuntu:24.04";
    public static final String DEFAULT_BASE_PACKAGES = "ca-certificates";

    public String baseImage;
    public String basePackages;
    public List<String> commands;

    public AptOpts() {
        this(Map.of());
    }

    public AptOpts(Map<String,?> opts) {
        this.baseImage = opts.containsKey("baseImage") ? opts.get("baseImage").toString() : DEFAULT_BASE_IMAGE;
        this.basePackages = opts.containsKey("basePackages") ? opts.get("basePackages").toString() : DEFAULT_BASE_PACKAGES;
        this.commands = opts.containsKey("commands") ? (List<String>) opts.get("commands") : null;
    }

    public AptOpts withBaseImage(String value) {
        this.baseImage = value;
        return this;
    }

    public AptOpts withBasePackages(String value) {
        this.basePackages = value;
        return this;
    }

    public AptOpts withCommands(List<String> value) {
        this.commands = value;
        return this;
    }

    @Override
    public String toString() {
        return String.format("AptOpts(baseImage=%s; basePackages=%s, commands=%s)",
                baseImage,
                basePackages,
                commands != null ? String.join(",", commands) : "null"
        );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        AptOpts aptOpts = (AptOpts) object;
        return Objects.equals(baseImage, aptOpts.baseImage)
                && Objects.equals(basePackages, aptOpts.basePackages)
                && Objects.equals(commands, aptOpts.commands);
    }

    @Override
    public int hashCode() {
        return Objects.hash(baseImage, basePackages, commands);
    }
}
