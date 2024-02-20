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

package io.seqera.wave.service.packages.model

import groovy.transform.CompileStatic
import io.seqera.wave.service.persistence.CondaPackageRecord

/**
 * Response model for packages
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
 */

@CompileStatic
class PackagesResponse {
    List<CondaPackageRecord> results

    PackagesResponse(List<CondaPackageRecord> results) {
        this.results = results
    }
}
