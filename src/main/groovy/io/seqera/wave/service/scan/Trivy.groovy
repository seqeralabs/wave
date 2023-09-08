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

package io.seqera.wave.service.scan

/**
 * Trivy constants
 *
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
interface Trivy {

    static final public String CACHE_MOUNT_PATH = '/root/.cache/'

    static final public String CONFIG_MOUNT_PATH = '/root/.docker/config.json'

    static final public String OUTPUT_FILE_NAME = 'report.json'
}
