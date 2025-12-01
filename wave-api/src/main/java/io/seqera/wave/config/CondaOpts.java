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
 * Conda build options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class CondaOpts {
    final public static String DEFAULT_MAMBA_IMAGE = "mambaorg/micromamba:1.5.10-noble";
    final public static String DEFAULT_MAMBA_IMAGE_V2 = "mambaorg/micromamba:2.1.1";
    final public static String DEFAULT_PACKAGES = "conda-forge::procps-ng";
    final public static String DEFAULT_BASE_IMAGE = "ubuntu:24.04";

    public String mambaImage;
    public List<String> commands;
    public String basePackages;
    public String baseImage;

    static public CondaOpts v2() {
        return new CondaOpts(Map.of("mambaImage", DEFAULT_MAMBA_IMAGE_V2));
    }

    public CondaOpts() {
        this(Map.of());
    }
    public CondaOpts(Map<String,?> opts) {
        this.mambaImage = opts.containsKey("mambaImage") ? opts.get("mambaImage").toString(): DEFAULT_MAMBA_IMAGE;
        this.commands = opts.containsKey("commands") ? (List<String>)opts.get("commands") : null;
        this.basePackages = opts.containsKey("basePackages") ? (String)opts.get("basePackages") : DEFAULT_PACKAGES;
        this.baseImage = opts.containsKey("baseImage") ? opts.get("baseImage").toString(): DEFAULT_BASE_IMAGE;
    }

    public CondaOpts withMambaImage(String value) {
        this.mambaImage = value;
        return this;
    }

    public CondaOpts withCommands(List<String> value) {
        this.commands = value;
        return this;
    }

    public CondaOpts withBasePackages(String value) {
        this.basePackages = value;
        return this;
    }

    @Override
    public String toString() {
        return String.format("CondaOpts(mambaImage=%s; basePackages=%s, commands=%s, baseImage=%s)",
                mambaImage,
                basePackages,
                commands != null ? String.join(",", commands) : "null",
                baseImage
                );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CondaOpts condaOpts = (CondaOpts) object;
        return Objects.equals(mambaImage, condaOpts.mambaImage)
                && Objects.equals(commands, condaOpts.commands)
                && Objects.equals(basePackages, condaOpts.basePackages)
                && Objects.equals(baseImage, condaOpts.baseImage)
                ;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mambaImage, commands, basePackages, baseImage);
    }
}
