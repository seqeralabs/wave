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
 * CRAN/R build options
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public class CranOpts {
    final public static String DEFAULT_R_IMAGE = "rocker/r-ver:4.4.1";
    final public static String DEFAULT_PACKAGES = "littler r-cran-docopt";

    public String rImage;
    public List<String> commands;
    public String basePackages;

    public CranOpts() {
        this(Map.of());
    }

    public CranOpts(Map<String,?> opts) {
        this.rImage = opts.containsKey("rImage") ? opts.get("rImage").toString(): DEFAULT_R_IMAGE;
        this.commands = opts.containsKey("commands") ? (List<String>)opts.get("commands") : null;
        this.basePackages = opts.containsKey("basePackages") ? (String)opts.get("basePackages") : DEFAULT_PACKAGES;
    }

    public CranOpts withRImage(String value) {
        this.rImage = value;
        return this;
    }

    public CranOpts withCommands(List<String> value) {
        this.commands = value;
        return this;
    }

    public CranOpts withBasePackages(String value) {
        this.basePackages = value;
        return this;
    }

    @Override
    public String toString() {
        return String.format("CranOpts(rImage=%s; basePackages=%s, commands=%s)",
                rImage,
                basePackages,
                commands != null ? String.join(",", commands) : "null"
                );
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) return true;
        if (object == null || getClass() != object.getClass()) return false;
        CranOpts cranOpts = (CranOpts) object;
        return Objects.equals(rImage, cranOpts.rImage) && Objects.equals(commands, cranOpts.commands) && Objects.equals(basePackages, cranOpts.basePackages);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rImage, commands, basePackages);
    }
}