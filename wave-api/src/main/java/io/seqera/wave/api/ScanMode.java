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
 * Define the possible container security scan modes
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
public enum ScanMode {
    none,       // no scan is performed
    async,      // scan is carried out asynchronously once the build is complete
    required,   // scan completion is required for the container request to reach 'DONE' status
    ;

    public boolean asBoolean() {
        return this != none;
    }
}
