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
    public static final String CONDA_PIXI_V1 = "conda/pixi:v1";

    /**
     * Build template for Micromamba v1-based multi-stage builds
     */
    public static final String CONDA_MICROMAMBA_V1 = "conda/micromamba:v1";

    /**
     * Build template for Micromamba v2-based multi-stage builds
     */
    public static final String CONDA_MICROMAMBA_V2 = "conda/micromamba:v2";

    /**
     * Build template for R/CRAN package builds
     */
    public static final String CRAN_INSTALLR_V1 = "cran/installr:v1";

    private BuildTemplate() {
        // Prevent instantiation
    }

    /**
     * Returns the default build template based on the package type.
     *
     * @param packages The packages specification
     * @return The default template for the package type, or null if packages is null or has no type
     */
    public static String defaultTemplate(PackagesSpec packages) {
        if (packages == null) {
            return null;
        }
        if( packages.type == PackagesSpec.Type.CRAN ) {
            return CRAN_INSTALLR_V1;
        }
        return null;
    }
}
