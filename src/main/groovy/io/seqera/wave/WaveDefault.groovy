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

package io.seqera.wave
/**
 * Wave app defaults
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface WaveDefault {

    final static public String DOCKER_IO = 'docker.io'
    final static public String DOCKER_REGISTRY_1 = 'https://registry-1.docker.io'
    final static public String DOCKER_INDEX_V1 = 'https://index.docker.io/v1/'

    final static public Map<String,List<String>> ACCEPT_HEADERS = Map.of(
            'Accept', List.of(
                    'application/json',
                    'application/vnd.oci.image.index.v1+json',
                    'application/vnd.oci.image.manifest.v1+json',
                    'application/vnd.docker.distribution.manifest.v1+prettyjws',
                    'application/vnd.docker.distribution.manifest.v2+json',
                    'application/vnd.docker.distribution.manifest.list.v2+json' ) )


    final public static int[] HTTP_REDIRECT_CODES = List.of(301, 302, 303, 307, 308)

    final public static List<Integer> HTTP_SERVER_ERRORS = List.of(500, 502, 503, 504)

    final public static List<Integer> HTTP_RETRYABLE_ERRORS = List.of(429, 500, 502, 503, 504)
    
}
