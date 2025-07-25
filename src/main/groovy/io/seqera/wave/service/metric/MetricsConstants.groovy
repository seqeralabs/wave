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

package io.seqera.wave.service.metric
/**
 * Metric constants
 *
 * @author Munish Chouhan <munish.chouhan@seqera.io>
*/
interface MetricsConstants {

    static final public String PREFIX_FUSION =  'fusion'

    static final public String PREFIX_BUILDS =  'builds'

    static final public String PREFIX_PULLS =  'pulls'

    static final public String PREFIX_MIRRORS =  'mirrors'

    static final public String PREFIX_SCANS =  'scans'

    static final public String PREFIX_ORG =  'o'

    static final public String PREFIX_DAY =  'd'

    static final public String PREFIX_ARCH =  'a'

    static final public String ARM64 =  'arm64'

    static final public String AMD64 =  'amd64'

}
