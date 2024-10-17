/*
 *  Wave, containers provisioning service
 *  Copyright (c) 2023-2024, Seqera Labs
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Affero General Public License for more details.
 *
 *  You should have received a copy of the GNU Affero General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package io.seqera.wave.service.builder


import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
/**
 * Model build tracking info
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@EqualsAndHashCode
@ToString(includePackage = false, includeNames = true)
class BuildTrack {
    String id
    String targetImage
    boolean cached
    /**
     * use Boolean type with three state meaning:
     * - null: build is still in progress
     * - false: completed with failure
     * - true: completed with success
     */
    Boolean succeeded

    BuildTrack(String id, String targetImage, boolean cached, Boolean succeeded) {
        this.id = id
        this.targetImage = targetImage
        this.cached = cached
        this.succeeded = succeeded
    }
}
