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
interface MetricConstants {

    static final public String ANONYMOUS = 'anonymous'

    static final public String PREFIX_PULLS_DAY =  'pulls/d/'

    static final public String PREFIX_PULLS_ORG =  'pulls/o/'

    static final public String PREFIX_FUSION_DAY =  'fusion/d/'

    static final public String PREFIX_FUSION_ORG =  'fusion/o/'

    static final public String PREFIX_BUILDS_DAY =  'builds/d/'

    static final public String PREFIX_BUILDS_ORG =  'builds/o/'

}
