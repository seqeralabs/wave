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

package io.seqera.wave.tower.client

import groovy.transform.CompileStatic
import groovy.transform.ToString

/**
 * Model Tower service info response
 * 
 * @author Paolo Di Tommaso <paolo.ditommaso@gmail.com>
 */
@ToString(includePackage = false, includeNames = true)
@CompileStatic
class ServiceInfoResponse {

    @ToString(includePackage = false, includeNames = true)
    static class ServiceInfo {
        String version
        String apiVersion
        String commitId
        Boolean waveEnabled
    }

    ServiceInfo serviceInfo
}
