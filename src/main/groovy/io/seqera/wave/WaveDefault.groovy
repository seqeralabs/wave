/*
 *  Copyright (c) 2023, Seqera Labs.
 *
 *  This Source Code Form is subject to the terms of the Mozilla Public
 *  License, v. 2.0. If a copy of the MPL was not distributed with this
 *  file, You can obtain one at http://mozilla.org/MPL/2.0/.
 *
 *  This Source Code Form is "Incompatible With Secondary Licenses", as
 *  defined by the Mozilla Public License, v. 2.0.
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
    final static public String TOWER = 'tower'

    final static public Map<String,List<String>> ACCEPT_HEADERS = Map.of(
            'Accept', List.of(
                    'application/json',
                    'application/vnd.oci.image.index.v1+json',
                    'application/vnd.oci.image.manifest.v1+json',
                    'application/vnd.docker.distribution.manifest.v1+prettyjws',
                    'application/vnd.docker.distribution.manifest.v2+json',
                    'application/vnd.docker.distribution.manifest.list.v2+json' ) )


    final public static int[] HTTP_REDIRECT_CODES = [301, 302, 307, 308]

    final public static List<Integer> HTTP_SERVER_ERRORS = [429,502,503,504]

}
