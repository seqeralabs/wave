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

/**
 * Constants for container build templates.
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public final class BuildTemplate {

    /**
     * Build template for Pixi-based multi-stage builds
     */
    public static final String PIXI_V1 = "pixi/v1";

    /**
     * Build template for Micromamba v2-based multi-stage builds
     */
    public static final String MICROMAMBA_V2 = "micromamba/v2";

    private BuildTemplate() {
        // Prevent instantiation
    }
}